"""O assistente de doação: chain em LCEL com recuperação, prompt, modelo com tools e fallback.

    pergunta ─► recuperar ─► montar prompt ─► modelo (+ tools) ─► resposta
                (vetorial;     (trechos com     (até 3 rodadas      (texto, fontes,
                 palavras-      número para      de tool call)        tools, tokens)
                 chave no       citar)
                 fallback)

Se o provedor falhar depois do retry, ou o circuito estiver aberto, a resposta sai pelo fallback:
os trechos recuperados, com o aviso de que o assistente está indisponível e mode = "fallback".
"""

from __future__ import annotations

import json
import logging
import time

import pybreaker
from langchain_core.messages import ToolMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.runnables import RunnableLambda, RunnablePassthrough

from app.config import Settings
from app.errors import describe
from app.fallback import excerpt, fallback_answer, sources_of
from app.keyword_search import KeywordIndex
from app.knowledge import Chunk
from app.llm_text import message_text, usage_of
from app.resilience import BudgetExhausted, Deadline, ProviderGuard
from app.vectorstore import VectorStore

log = logging.getLogger("app.assistant")

_PERSONA = (
    "Você é o assistente de doação da Ravita, plataforma que conecta estabelecimentos com alimentos excedentes "
    "a instituições sociais. Quem pergunta é um doador, uma instituição ou alguém da equipe da Ravita.\n"
    "Responda em português do Brasil, de forma direta: no máximo 8 frases ou uma lista curta.\n"
    "Para saber o que está disponível agora, use a ferramenta buscar_ofertas_disponiveis. Para saber quais "
    "instituições recebem uma categoria de alimento, use a ferramenta instituicoes_que_aceitam. Nunca invente "
    "ofertas, instituições, quantidades ou nomes: use só o que as ferramentas devolverem.\n"
    "Você não garante que um alimento está seguro: a decisão é de quem o manipula."
)

RAG_SYSTEM = _PERSONA + (
    "\n\nUse os trechos da base de conhecimento abaixo para regras, leis, temperaturas e prazos, e cite o número "
    "do trecho usado entre colchetes, por exemplo [1]. Se os trechos e as ferramentas não respondem à pergunta, "
    "diga que não sabe e sugira procurar a vigilância sanitária local ou a equipe da Ravita; não complete com "
    "conhecimento próprio sobre leis, temperaturas ou prazos.\n\nTrechos da base de conhecimento:\n{context}"
)

PLAIN_SYSTEM = _PERSONA + (
    "\n\nResponda com o seu conhecimento geral. Se não tiver certeza sobre uma lei, temperatura ou prazo, "
    "diga que não sabe."
)


def format_context(chunks: list[Chunk]) -> str:
    if not chunks:
        return "(nenhum trecho encontrado para esta pergunta)"
    return "\n\n".join(f"[{position}] {chunk.title} — {chunk.section} (fonte: {chunk.source})\n{chunk.text}"
                       for position, chunk in enumerate(chunks, start=1))


def chunk_from_document(document) -> Chunk:
    metadata = document.metadata or {}
    text = document.page_content.split("\n", 1)[1] if "\n" in document.page_content else document.page_content
    return Chunk(metadata.get("chunk_id", ""), metadata.get("file", ""), metadata.get("title", ""),
                 metadata.get("source", ""), metadata.get("section", ""), text)


class Assistant:
    def __init__(self, settings: Settings, chunks: list[Chunk], guard: ProviderGuard, llm=None,
                 embeddings=None, vector: VectorStore | None = None, tools: list | None = None) -> None:
        self.settings = settings
        self.guard = guard
        self.keyword = KeywordIndex(chunks)
        self.embeddings = embeddings
        self.vector = vector
        self.tools = {tool.name: tool for tool in (tools or [])}
        self.llm = llm.bind_tools(list(self.tools.values())) if llm is not None and self.tools else llm
        self.rag_prompt = ChatPromptTemplate.from_messages(
            [("system", RAG_SYSTEM), ("human", "{question}"), MessagesPlaceholder("scratchpad", optional=True)])
        self.plain_prompt = ChatPromptTemplate.from_messages(
            [("system", PLAIN_SYSTEM), ("human", "{question}"), MessagesPlaceholder("scratchpad", optional=True)])
        # a chain: cada passo recebe o dicionário do anterior, acrescido do próprio resultado
        self.chain = (
            RunnablePassthrough.assign(retrieved=RunnableLambda(self._retrieve))
            | RunnablePassthrough.assign(context=RunnableLambda(lambda state: format_context(state["retrieved"][0])))
            | RunnablePassthrough.assign(generation=RunnableLambda(self._generate))
            | RunnableLambda(self._present)
        )

    # ------------------------------------------------------------ API
    def answer(self, question: str, use_retrieval: bool = True) -> dict:
        started = time.monotonic()
        result = self.chain.invoke({"question": question.strip(), "use_retrieval": use_retrieval,
                                    "deadline": Deadline(self.settings.request_budget)})
        result["latencyMs"] = int((time.monotonic() - started) * 1000)
        log.info("Pergunta respondida: mode=%s retrieval=%s tools=%s tokens=%s/%s latency=%s ms",
                 result["mode"], result["retrieval"], [call["name"] for call in result["toolCalls"]],
                 result["usage"]["inputTokens"], result["usage"]["outputTokens"], result["latencyMs"])
        return result

    def health(self) -> dict:
        return {"status": "UP", "model": self.settings.chat_model, "llmConfigured": self.llm is not None,
                "circuitBreaker": self.guard.state, "knowledgeChunks": len(self.keyword),
                "vectorStore": {"status": self.vector.status if self.vector else "disabled",
                                "detail": self.vector.detail if self.vector else ""}}

    # ------------------------------------------------------------ passos da chain
    def _retrieve(self, state: dict) -> tuple[list[Chunk], str]:
        if not state["use_retrieval"]:
            return [], "none"
        question, k = state["question"], self.settings.retrieval_k
        if self.vector is not None and self.vector.ready and self.embeddings is not None:
            try:
                vector = self.guard.call(lambda: self.embeddings.embed_query(question), state["deadline"],
                                         "embedding da pergunta")
                return [chunk_from_document(doc) for doc in self.vector.search_by_vector(vector, k)], "vector"
            except Exception as error:
                log.warning("Recuperação vetorial falhou (%s); usando busca por palavras-chave", describe(error))
        return self.keyword.search(question, k), "keyword"

    def _generate(self, state: dict) -> dict:
        """Prompt + modelo com tools. Devolve o AIMessage final ou o motivo do fallback."""
        generation = {"message": None, "toolCalls": [], "inputTokens": 0, "outputTokens": 0, "llmCalls": 0,
                      "fallbackReason": None}
        if self.llm is None:
            generation["fallbackReason"] = "GOOGLE_API_KEY não configurada"
            return generation
        prompt = self.rag_prompt if state["use_retrieval"] else self.plain_prompt
        variables = {"question": state["question"], "context": state["context"], "scratchpad": []}
        try:
            for _ in range(self.settings.max_tool_rounds + 1):
                messages = prompt.invoke(variables).to_messages()
                message = self.guard.call(lambda: self.llm.invoke(messages), state["deadline"], "chamada ao modelo")
                tokens_in, tokens_out = usage_of(message)
                generation["inputTokens"] += tokens_in
                generation["outputTokens"] += tokens_out
                generation["llmCalls"] += 1
                generation["message"] = message
                if not getattr(message, "tool_calls", None):
                    break
                variables["scratchpad"] = variables["scratchpad"] + [message] + self._run_tools(message, generation)
        except pybreaker.CircuitBreakerError:
            generation["fallbackReason"] = "circuito aberto depois de falhas seguidas do provedor"
        except BudgetExhausted:
            generation["fallbackReason"] = "tempo limite da pergunta esgotado"
        except Exception as error:
            generation["fallbackReason"] = f"falha do provedor: {describe(error)}"
            log.warning("Chamada ao modelo falhou depois do retry: %s", describe(error))
        return generation

    def _run_tools(self, message, generation: dict) -> list[ToolMessage]:
        results = []
        for call in message.tool_calls:
            tool = self.tools.get(call["name"])
            try:
                output = tool.invoke(call.get("args") or {}) if tool else {"erro": "ferramenta desconhecida"}
            except Exception as error:  # argumentos inválidos gerados pelo modelo, por exemplo
                output = {"erro": describe(error)}
            ok = isinstance(output, dict) and "erro" not in output
            count = output.get("total") if isinstance(output, dict) else None
            generation["toolCalls"].append({"name": call["name"], "args": call.get("args") or {}, "ok": ok,
                                            "resultCount": count})
            results.append(ToolMessage(content=json.dumps(output, ensure_ascii=False, default=str),
                                       tool_call_id=call.get("id") or call["name"], name=call["name"]))
        return results

    def _present(self, state: dict) -> dict:
        chunks, retrieval = state["retrieved"]
        generation = state["generation"]
        response = {
            "question": state["question"],
            "useRetrieval": state["use_retrieval"],
            "retrieval": retrieval,
            "sources": sources_of(chunks),
            "toolCalls": generation["toolCalls"],
            "usage": {"inputTokens": generation["inputTokens"], "outputTokens": generation["outputTokens"],
                      "llmCalls": generation["llmCalls"]},
            "model": self.settings.chat_model,
            "circuitBreaker": self.guard.state,
        }
        text = message_text(generation["message"]) if generation["message"] is not None else ""
        if generation["fallbackReason"] is None and text:
            response.update(mode="llm", answer=text, notice=None)
            return response
        reason = generation["fallbackReason"] or "o modelo não devolveu texto"
        if not chunks:  # o fallback sempre mostra trechos, mesmo quando a pergunta veio sem recuperação
            chunks = self.keyword.search(state["question"], self.settings.retrieval_k)
            response["retrieval"], response["sources"] = "keyword", sources_of(chunks)
        answer, notice = fallback_answer(chunks, reason)
        response.update(mode="fallback", answer=answer, notice=notice)
        for source, chunk in zip(response["sources"], chunks):
            source["excerpt"] = excerpt(chunk.text, 200)
        return response
