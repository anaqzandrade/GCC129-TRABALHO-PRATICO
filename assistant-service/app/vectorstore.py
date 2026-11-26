"""Base vetorial no PostgreSQL com pgvector (assistant-db), pelo PGVectorStore do langchain-postgres."""

from __future__ import annotations

import logging
import threading

import psycopg
from langchain_core.documents import Document

from app.config import Settings
from app.errors import describe, is_retryable
from app.knowledge import Chunk

log = logging.getLogger("app.vectorstore")


class VectorStore:
    """Estado: disabled, connecting, empty, ingesting, ready ou unavailable."""

    def __init__(self, settings: Settings, embeddings) -> None:
        self.settings = settings
        self.embeddings = embeddings
        self.status = "disabled" if not settings.vector_enabled or embeddings is None else "connecting"
        self.detail = ""
        self._store = None
        self._lock = threading.Lock()

    @property
    def ready(self) -> bool:
        return self.status == "ready" and self._store is not None

    # ------------------------------------------------------------ banco
    def _connect(self):
        return psycopg.connect(self.settings.psycopg_dsn, autocommit=True)

    def row_count(self) -> int | None:
        with self._connect() as connection:
            exists = connection.execute("SELECT to_regclass(%s)", (self.settings.vector_table,)).fetchone()[0]
            if exists is None:
                return None
            return connection.execute(f'SELECT count(*) FROM "{self.settings.vector_table}"').fetchone()[0]

    def _create_table(self, vector_size: int, drop: bool) -> None:
        from langchain_postgres import PGEngine

        with self._connect() as connection:
            connection.execute("CREATE EXTENSION IF NOT EXISTS vector")
            if drop:
                connection.execute(f'DROP TABLE IF EXISTS "{self.settings.vector_table}"')
        engine = PGEngine.from_connection_string(url=self.settings.sqlalchemy_url)
        engine.init_vectorstore_table(table_name=self.settings.vector_table, vector_size=vector_size)

    def _open(self):
        from langchain_postgres import PGEngine, PGVectorStore

        engine = PGEngine.from_connection_string(url=self.settings.sqlalchemy_url)
        return PGVectorStore.create_sync(engine=engine, table_name=self.settings.vector_table,
                                         embedding_service=self.embeddings)

    # ------------------------------------------------------------ ingestão
    def ingest(self, chunks: list[Chunk], force: bool = False, batch_size: int = 20) -> int:
        """Gera os embeddings dos trechos e grava na tabela. Devolve quantos trechos foram gravados."""
        with self._lock:
            self.status = "ingesting"
            vector_size = len(_with_retry(lambda: self.embeddings.embed_query("dimensão do vetor")))
            count = None if force else self.row_count()
            if count is None:
                self._create_table(vector_size, drop=force)
            store = self._open()
            documents = [Document(page_content=chunk.content, metadata=chunk.as_metadata()) for chunk in chunks]
            ids = [chunk.id for chunk in chunks]
            for start in range(0, len(documents), batch_size):
                batch, batch_ids = documents[start:start + batch_size], ids[start:start + batch_size]
                _with_retry(lambda: store.add_documents(batch, ids=batch_ids))
            self._store = store
            self.status, self.detail = "ready", f"{len(documents)} trechos"
            log.info("Ingestão concluída: %s trechos em %s (vetores de %s dimensões)", len(documents),
                     self.settings.vector_table, vector_size)
            return len(documents)

    def prepare(self, chunks: list[Chunk]) -> None:
        """Na subida: abre a base se já tiver trechos; se estiver vazia, faz a ingestão."""
        if self.status == "disabled":
            return
        try:
            count = self.row_count()
            if count:
                self._store = self._open()
                self.status, self.detail = "ready", f"{count} trechos"
                log.info("Base vetorial pronta: %s trechos em %s", count, self.settings.vector_table)
                return
            self.status = "empty"
            self.ingest(chunks)
        except Exception as error:  # a base vetorial é opcional: sem ela, o fallback usa palavras-chave
            self.status, self.detail = "unavailable", describe(error)
            log.warning("Base vetorial indisponível (%s); a recuperação usa busca por palavras-chave", self.detail)

    def search_by_vector(self, vector: list[float], k: int) -> list[Document]:
        return self._store.similarity_search_by_vector(vector, k=k)


def _with_retry(fn, attempts: int = 5):
    """Retry da ingestão: no nível gratuito, o limite por minuto pode responder 429."""
    import time

    for attempt in range(1, attempts + 1):
        try:
            return fn()
        except Exception as error:
            if attempt == attempts or not is_retryable(error):
                raise
            wait = min(2 ** attempt, 30)
            log.warning("Ingestão: tentativa %s falhou (%s); nova tentativa em %s s", attempt, describe(error), wait)
            time.sleep(wait)
