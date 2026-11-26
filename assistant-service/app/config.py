"""Configuração lida de variáveis de ambiente, como nos serviços Java (nada de segredo no código)."""

import os
from dataclasses import dataclass, field
from pathlib import Path


def _env(name: str, default: str) -> str:
    value = os.getenv(name)
    return default if value is None or value.strip() == "" else value.strip()


def _float(name: str, default: float) -> float:
    return float(_env(name, str(default)))


def _int(name: str, default: int) -> int:
    return int(_env(name, str(default)))


def _bool(name: str, default: bool) -> bool:
    return _env(name, "true" if default else "false").lower() in ("1", "true", "yes", "sim")


@dataclass(frozen=True)
class Settings:
    port: int = field(default_factory=lambda: _int("SERVER_PORT", 8095))
    # provedor (Gemini). Sem chave, o serviço sobe e responde sempre pelo fallback.
    api_key: str = field(default_factory=lambda: _env("GOOGLE_API_KEY", ""))
    chat_model: str = field(default_factory=lambda: _env("GEMINI_CHAT_MODEL", "gemini-3.1-flash-lite"))
    embedding_model: str = field(default_factory=lambda: _env("GEMINI_EMBEDDING_MODEL", "models/gemini-embedding-2"))
    temperature: float = field(default_factory=lambda: _float("LLM_TEMPERATURE", 0.2))
    # resiliência (doc 07, seção 5)
    llm_timeout: float = field(default_factory=lambda: _float("LLM_TIMEOUT_SECONDS", 10.0))
    llm_max_attempts: int = field(default_factory=lambda: _int("LLM_MAX_ATTEMPTS", 3))
    request_budget: float = field(default_factory=lambda: _float("REQUEST_BUDGET_SECONDS", 12.0))
    breaker_fail_max: int = field(default_factory=lambda: _int("BREAKER_FAIL_MAX", 3))
    breaker_reset_seconds: int = field(default_factory=lambda: _int("BREAKER_RESET_SECONDS", 30))
    max_tool_rounds: int = field(default_factory=lambda: _int("MAX_TOOL_ROUNDS", 3))
    # recuperação
    retrieval_k: int = field(default_factory=lambda: _int("RETRIEVAL_K", 4))
    chunk_size: int = field(default_factory=lambda: _int("CHUNK_SIZE", 800))
    chunk_overlap: int = field(default_factory=lambda: _int("CHUNK_OVERLAP", 100))
    knowledge_dir: Path = field(default_factory=lambda: Path(_env("KNOWLEDGE_DIR", str(Path(__file__).resolve().parent.parent / "knowledge"))))
    vector_enabled: bool = field(default_factory=lambda: _bool("VECTOR_STORE_ENABLED", True))
    vector_table: str = field(default_factory=lambda: _env("VECTOR_TABLE", "ravita_knowledge"))
    db_host: str = field(default_factory=lambda: _env("DB_HOST", "localhost"))
    db_port: int = field(default_factory=lambda: _int("DB_PORT", 5432))
    db_name: str = field(default_factory=lambda: _env("DB_NAME", "assistant_db"))
    db_user: str = field(default_factory=lambda: _env("DB_USERNAME", "ravita"))
    db_password: str = field(default_factory=lambda: _env("DB_PASSWORD", "ravita"))
    # tools: APIs dos serviços de domínio
    offer_url: str = field(default_factory=lambda: _env("OFFER_SERVICE_URL", "http://localhost:8082"))
    organization_url: str = field(default_factory=lambda: _env("ORGANIZATION_SERVICE_URL", "http://localhost:8081"))
    tool_timeout: float = field(default_factory=lambda: _float("TOOL_TIMEOUT_SECONDS", 3.0))

    @property
    def sqlalchemy_url(self) -> str:
        return f"postgresql+psycopg://{self.db_user}:{self.db_password}@{self.db_host}:{self.db_port}/{self.db_name}"

    @property
    def psycopg_dsn(self) -> str:
        return (f"host={self.db_host} port={self.db_port} dbname={self.db_name} "
                f"user={self.db_user} password={self.db_password} connect_timeout=3")
