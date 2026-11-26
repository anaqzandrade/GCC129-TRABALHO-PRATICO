"""Correlation ID da requisição atual, para os logs e para as chamadas às tools."""

import contextvars
import logging

CORRELATION_ID: contextvars.ContextVar[str] = contextvars.ContextVar("correlation_id", default="-")


class CorrelationIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = CORRELATION_ID.get()
        return True


def configure_logging() -> None:
    handler = logging.StreamHandler()
    handler.addFilter(CorrelationIdFilter())
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)5s [%(correlation_id)s] %(name)s: %(message)s"))
    root = logging.getLogger()
    root.handlers[:] = [handler]
    root.setLevel(logging.INFO)
    for noisy in ("httpx", "httpcore", "google_genai", "google_genai.models", "urllib3"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
