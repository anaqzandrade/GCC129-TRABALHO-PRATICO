"""API do assistant-service (FastAPI). Contrato no doc 07, seção 6."""

from __future__ import annotations

import logging
import threading
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from app.assistant import Assistant
from app.config import Settings
from app.context import CORRELATION_ID, configure_logging
from app.knowledge import load_chunks
from app.providers import build_chat_model, build_embeddings
from app.resilience import ProviderGuard
from app.tools import build_tools
from app.vectorstore import VectorStore

log = logging.getLogger("app.main")
HEADER = "X-Correlation-ID"


class QuestionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question: str = Field(min_length=3, max_length=1000)
    useRetrieval: bool = True


def error(status: int, code: str, message: str, correlation_id: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={"code": code, "message": message,
                                                     "correlationId": correlation_id})


def build_assistant(settings: Settings) -> Assistant:
    chunks = load_chunks(settings.knowledge_dir, settings.chunk_size, settings.chunk_overlap)
    embeddings = build_embeddings(settings)
    vector = VectorStore(settings, embeddings)
    guard = ProviderGuard(settings.llm_timeout, settings.llm_max_attempts, settings.breaker_fail_max,
                          settings.breaker_reset_seconds)
    assistant = Assistant(settings, chunks, guard, llm=build_chat_model(settings), embeddings=embeddings,
                          vector=vector, tools=build_tools(settings))
    log.info("Base de conhecimento: %s trechos de %s arquivos", len(chunks), len({c.file for c in chunks}))
    # a ingestão roda em segundo plano: o serviço já responde (pela busca por palavras-chave) enquanto ela acontece
    threading.Thread(target=vector.prepare, args=(chunks,), name="vector-prepare", daemon=True).start()
    return assistant


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    configure_logging()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.assistant = build_assistant(settings)
        yield

    app = FastAPI(title="Ravita assistant-service", version="0.0.1", lifespan=lifespan)

    @app.middleware("http")
    async def correlation(request: Request, call_next):
        correlation_id = request.headers.get(HEADER) or str(uuid.uuid4())
        request.state.correlation_id = correlation_id
        token = CORRELATION_ID.set(correlation_id)
        started = time.monotonic()
        try:
            response = await call_next(request)
        finally:
            CORRELATION_ID.reset(token)
        response.headers[HEADER] = correlation_id
        if request.url.path != "/health":
            log.info("%s %s -> %s (%s ms) [%s]", request.method, request.url.path, response.status_code,
                     int((time.monotonic() - started) * 1000), correlation_id)
        return response

    @app.exception_handler(RequestValidationError)
    async def invalid(request: Request, exc: RequestValidationError):
        details = "; ".join(f"{'.'.join(str(p) for p in e['loc'][1:]) or 'corpo'}: {e['msg']}" for e in exc.errors())
        return error(400, "INVALID_REQUEST", details or "Requisição inválida.", request.state.correlation_id)

    @app.exception_handler(Exception)
    async def unexpected(request: Request, exc: Exception):
        log.exception("Erro inesperado")
        return error(500, "INTERNAL_ERROR", "Erro inesperado no assistente.", request.state.correlation_id)

    @app.post("/api/v1/assistant/questions")
    def ask(body: QuestionRequest, request: Request):
        # endpoint síncrono: o FastAPI roda numa thread; o correlation id é fixado de novo nela
        token = CORRELATION_ID.set(request.state.correlation_id)
        try:
            return request.app.state.assistant.answer(body.question, body.useRetrieval)
        finally:
            CORRELATION_ID.reset(token)

    @app.get("/health")
    def health(request: Request):
        return request.app.state.assistant.health()

    return app


app = create_app()
