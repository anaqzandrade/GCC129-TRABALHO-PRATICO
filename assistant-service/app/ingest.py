"""Ingestão da base de conhecimento: lê os Markdown, divide, gera embeddings e grava no PGVector.

    docker compose run --rm assistant-service python -m app.ingest           # só se a tabela estiver vazia
    docker compose run --rm assistant-service python -m app.ingest --force   # apaga e grava de novo

Na subida do serviço, a mesma ingestão roda sozinha quando a tabela está vazia.
"""

from __future__ import annotations

import argparse
import logging
import sys

from app.config import Settings
from app.context import configure_logging
from app.knowledge import load_chunks
from app.providers import build_embeddings
from app.vectorstore import VectorStore

log = logging.getLogger("app.ingest")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Ingestão da base de conhecimento da Ravita")
    parser.add_argument("--force", action="store_true", help="apaga a tabela e grava todos os trechos de novo")
    args = parser.parse_args(argv)
    configure_logging()
    settings = Settings()
    embeddings = build_embeddings(settings)
    if embeddings is None:
        log.error("Defina GOOGLE_API_KEY no .env para gerar os embeddings")
        return 1
    chunks = load_chunks(settings.knowledge_dir, settings.chunk_size, settings.chunk_overlap)
    log.info("%s trechos lidos de %s", len(chunks), settings.knowledge_dir)
    store = VectorStore(settings, embeddings)
    existing = store.row_count()
    if existing and not args.force:
        log.info("A tabela %s já tem %s trechos; use --force para refazer", settings.vector_table, existing)
        return 0
    store.ingest(chunks, force=args.force)
    return 0


if __name__ == "__main__":
    sys.exit(main())
