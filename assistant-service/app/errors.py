"""Classificação das falhas do provedor: o que vale tentar de novo e o que não vale.

Retry só para timeout, 429 (limite de requisições) e 5xx. Chave inválida, pedido malformado ou
modelo inexistente (4xx) falham igual na segunda tentativa: tentar de novo só aumenta a latência.
"""

from __future__ import annotations

import re

RETRYABLE_STATUS = {408, 429, 500, 502, 503, 504}
_RETRYABLE_NAMES = ("timeout", "ratelimit", "resourceexhausted", "serviceunavailable", "internalservererror",
                    "servererror", "deadlineexceeded", "connecterror", "connectionerror", "remoteprotocolerror")
_RETRYABLE_TEXT = ("resource_exhausted", "unavailable", "deadline_exceeded", "timed out", "timeout",
                   "rate limit", "overloaded", "internal error", "try again")
_STATUS_IN_TEXT = re.compile(r"\b(4\d\d|5\d\d)\b")


def _chain(exc: BaseException):
    seen = set()
    while exc is not None and id(exc) not in seen:
        seen.add(id(exc))
        yield exc
        exc = exc.__cause__ or exc.__context__


def status_code(exc: BaseException) -> int | None:
    """Código HTTP da falha, quando a exceção (ou a causa dela) informa um."""
    for error in _chain(exc):
        for attribute in ("status_code", "code", "status"):
            value = getattr(error, attribute, None)
            if isinstance(value, int) and 100 <= value <= 599:
                return value
        response = getattr(error, "response", None)
        value = getattr(response, "status_code", None)
        if isinstance(value, int):
            return value
    return None


def is_retryable(exc: BaseException) -> bool:
    status = status_code(exc)
    if status is not None:
        return status in RETRYABLE_STATUS
    for error in _chain(exc):
        if isinstance(error, TimeoutError):
            return True
        name = type(error).__name__.lower()
        if any(fragment in name for fragment in _RETRYABLE_NAMES):
            return True
        text = str(error).lower()
        if any(fragment in text for fragment in _RETRYABLE_TEXT):
            return True
        match = _STATUS_IN_TEXT.search(text)
        if match:
            return int(match.group(1)) in RETRYABLE_STATUS
    return False


def describe(exc: BaseException) -> str:
    """Resumo curto para log e para o aviso do fallback, sem despejar a mensagem inteira do provedor."""
    status = status_code(exc)
    message = str(exc).strip().splitlines()[0] if str(exc).strip() else ""
    if len(message) > 160:
        message = message[:157] + "..."
    prefix = type(exc).__name__ + (f" {status}" if status else "")
    return f"{prefix}: {message}" if message else prefix
