"""Criação dos clientes do Gemini. Sem GOOGLE_API_KEY, nenhum cliente é criado e o serviço usa o fallback."""

from __future__ import annotations

import logging

from app.config import Settings

log = logging.getLogger("app.providers")


def build_chat_model(settings: Settings):
    if not settings.api_key:
        log.warning("GOOGLE_API_KEY não configurada: o assistente vai responder pelo fallback")
        return None
    try:
        from langchain_google_genai import ChatGoogleGenerativeAI

        # max_retries=0: o retry é nosso (tenacity), com backoff e só para falhas transitórias
        return ChatGoogleGenerativeAI(model=settings.chat_model, temperature=settings.temperature,
                                      timeout=settings.llm_timeout, max_retries=0)
    except Exception as error:  # o serviço sobe mesmo assim e responde pelo fallback
        log.error("Não foi possível criar o cliente do modelo %s: %s", settings.chat_model, error)
        return None


def build_embeddings(settings: Settings):
    if not settings.api_key:
        return None
    try:
        from langchain_google_genai import GoogleGenerativeAIEmbeddings

        return GoogleGenerativeAIEmbeddings(model=settings.embedding_model)
    except Exception as error:
        log.error("Não foi possível criar o cliente de embeddings %s: %s", settings.embedding_model, error)
        return None
