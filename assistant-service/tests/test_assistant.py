"""O assistente inteiro com um modelo falso: tools, fallback e circuito, sem rede e sem chave."""

import pytest

pytest.importorskip("langchain_core")
pytest.importorskip("pybreaker")
pytest.importorskip("psycopg")

from langchain_core.messages import AIMessage  # noqa: E402
from langchain_core.tools import tool  # noqa: E402

from app.assistant import Assistant  # noqa: E402
from app.config import Settings  # noqa: E402
from app.knowledge import load_chunks  # noqa: E402
from app.resilience import ProviderGuard  # noqa: E402
from tests.conftest import KNOWLEDGE  # noqa: E402


class FakeModel:
    """Primeira chamada pede a tool; a segunda responde com texto."""

    def __init__(self, replies):
        self.replies, self.calls = list(replies), 0

    def bind_tools(self, tools):
        return self

    def invoke(self, messages):
        self.calls += 1
        reply = self.replies.pop(0)
        if isinstance(reply, Exception):
            self.replies.insert(0, reply)
            raise reply
        return reply


class ServerDown(Exception):
    code = 503


@tool
def instituicoes_que_aceitam(categoria: str) -> dict:
    """Instituições que recebem a categoria."""
    return {"categoria": "DAIRY", "total": 1, "instituicoes": [{"instituicao": "Casa Esperança"}]}


def make(model):
    settings = Settings(api_key="", vector_enabled=False)
    guard = ProviderGuard(1.0, 3, 3, 30, backoff_min=0, backoff_max=0)
    chunks = load_chunks(KNOWLEDGE, settings.chunk_size, settings.chunk_overlap)
    return Assistant(settings, chunks, guard, llm=model, tools=[instituicoes_que_aceitam])


def test_model_calls_the_tool_and_answers_with_sources():
    model = FakeModel([
        AIMessage(content="", tool_calls=[{"name": "instituicoes_que_aceitam", "args": {"categoria": "laticínios"},
                                           "id": "call-1"}],
                  usage_metadata={"input_tokens": 500, "output_tokens": 20, "total_tokens": 520}),
        AIMessage(content="Pode doar dentro da validade [1]. A Casa Esperança recebe laticínios.",
                  usage_metadata={"input_tokens": 600, "output_tokens": 40, "total_tokens": 640}),
    ])
    result = make(model).answer("Posso doar iogurte que vence amanhã? Para quem?")
    assert result["mode"] == "llm"
    assert result["retrieval"] == "keyword"  # sem base vetorial neste teste
    assert result["sources"]
    assert result["toolCalls"] == [{"name": "instituicoes_que_aceitam", "args": {"categoria": "laticínios"},
                                    "ok": True, "resultCount": 1}]
    assert result["usage"] == {"inputTokens": 1100, "outputTokens": 60, "llmCalls": 2}


def test_provider_down_gives_fallback_and_then_opens_the_circuit():
    model = FakeModel([ServerDown("503 UNAVAILABLE")])
    assistant = make(model)
    for _ in range(3):
        result = assistant.answer("Por quanto tempo a refeição pode ficar quente?")
        assert result["mode"] == "fallback"
        assert "6 horas" in result["answer"]
    assert assistant.guard.state == "open"
    calls = model.calls
    result = assistant.answer("Por quanto tempo a refeição pode ficar quente?")
    assert "circuito aberto" in result["notice"]
    assert model.calls == calls


def test_without_api_key_the_answer_comes_from_the_fallback():
    result = make(None).answer("Quais informações vão na etiqueta da refeição transportada?", use_retrieval=False)
    assert result["mode"] == "fallback"
    assert "GOOGLE_API_KEY" in result["notice"]
    assert result["retrieval"] == "keyword" and result["sources"]
