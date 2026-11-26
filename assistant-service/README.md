# assistant-service

Assistente de doação da Ravita (doc 07). O doador pergunta se pode doar um alimento e para quem; a instituição
pergunta o que há disponível e como conservar. As regras vêm de uma base de conhecimento própria (RAG) e os
dados do sistema vêm de tools que chamam as APIs dos serviços.

- Python 3.12, FastAPI, LangChain (chain em LCEL, tools), Gemini pela API do Google
- Porta **8095**; o Portal BFF repassa as perguntas em `POST /portal/v1/assistant/questions`
- Banco vetorial próprio: `assistant-db` (PostgreSQL 17 com pgvector), na rede interna `assistant-data`

## Configurar a chave

Crie uma chave no Google AI Studio (nível gratuito) e coloque no `.env` da raiz, que não vai para o git:

```
GOOGLE_API_KEY=sua-chave
```

Sem chave, o serviço sobe do mesmo jeito e responde sempre pelo fallback.

## Endpoint

`POST /api/v1/assistant/questions`

```json
{ "question": "Tenho iogurtes que vencem em 3 dias. Posso doar? Quem recebe laticínios?", "useRetrieval": true }
```

| Campo da resposta | O que é |
|---|---|
| `answer` | Resposta em português, citando os trechos usados como `[1]`, `[2]` |
| `mode` | `llm` (resposta do modelo) ou `fallback` (modelo indisponível: devolve os trechos da base) |
| `notice` | No fallback, o motivo (circuito aberto, falha do provedor, chave ausente) |
| `sources` | Trechos recuperados: título, seção e fonte de cada `[n]` |
| `toolCalls` | Tools que o modelo chamou, com os argumentos e quantos resultados vieram |
| `retrieval` | `vector` (pgvector), `keyword` (busca por palavras-chave) ou `none` |
| `usage` | Tokens de entrada e saída e número de chamadas ao modelo |
| `latencyMs` | Tempo da resposta no servidor |

`useRetrieval: false` existe para a avaliação comparar respostas com e sem RAG. O Portal BFF não repassa esse
campo: no portal, a recuperação está sempre ligada.

`GET /health` mostra o estado do circuit breaker, da base vetorial e se o modelo está configurado.

## Pipeline do RAG

1. **Base de conhecimento**: `knowledge/*.md` — Lei 15.224/2025, RDC 216/2004 da Anvisa, conservação por
   categoria e regras da Ravita. Cada arquivo tem título e fonte.
2. **Ingestão** (`app/ingest.py`): divide em trechos de 800 caracteres com 100 de sobreposição
   (`RecursiveCharacterTextSplitter`), gera os embeddings com `gemini-embedding-2` e grava no pgvector
   (`PGVectorStore`, tabela `ravita_knowledge`). Roda sozinha na subida quando a tabela está vazia, ou com:
   ```
   docker compose run --rm assistant-service python -m app.ingest --force
   ```
3. **Recuperação**: embedding da pergunta e busca pelos 4 trechos mais próximos.
4. **Geração**: prompt com os trechos numerados, modelo com as tools e uma segunda chamada com o resultado
   das tools.

## Tools

| Tool | Chama | Para |
|---|---|---|
| `buscar_ofertas_disponiveis(categoria, cidade)` | `GET offer-service/api/v1/offers/available` (modelo de leitura do CQRS) | o que há disponível agora |
| `instituicoes_que_aceitam(categoria, cidade)` | `GET organization-service/api/v1/organizations?type=INSTITUTION&active=true` | quem recebe a categoria |

## Resiliência

| Mecanismo | Configuração |
|---|---|
| Timeout | 10 s por chamada ao Gemini, dentro de um orçamento de 12 s por pergunta |
| Retry com backoff (tenacity) | até 3 tentativas, espera exponencial de 0,5 s a 4 s, só para timeout, 429 e 5xx |
| Circuit breaker (pybreaker) | abre depois de 3 falhas seguidas e tenta de novo após 30 s; cada mudança de estado vai para o log |
| Fallback | trechos da base com o aviso de indisponibilidade; a recuperação cai para palavras-chave se o embedding falhar |

## Testes e avaliação

```powershell
docker build --target test ./assistant-service                          # testes unitários e de contrato
docker compose exec assistant-service python -m eval.run_eval            # avaliação, latência e custo (~6 min)
docker compose cp assistant-service:/tmp/avaliacao-llm.md .\avaliacao-llm.md
```
