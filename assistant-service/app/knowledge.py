"""Leitura da base de conhecimento (knowledge/*.md) e divisão em trechos.

Cada arquivo começa com "# Título" e uma linha "Fonte: ...". As seções "## ..." viram o contexto de cada
trecho: o mesmo texto é usado na base vetorial e na busca por palavras-chave do fallback.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from pathlib import Path

_NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "ravita/assistant/knowledge")


@dataclass(frozen=True)
class Section:
    file: str
    title: str
    source: str
    heading: str
    text: str


@dataclass(frozen=True)
class Chunk:
    id: str
    file: str
    title: str
    source: str
    section: str
    text: str
    metadata: dict = field(default_factory=dict, compare=False)

    @property
    def content(self) -> str:
        """Texto indexado: título e seção entram junto, para o trecho não perder o contexto."""
        return f"{self.title} — {self.section}\n{self.text}"

    def as_metadata(self) -> dict:
        return {"chunk_id": self.id, "file": self.file, "title": self.title, "source": self.source,
                "section": self.section}


def parse_markdown(file: str, markdown: str) -> list[Section]:
    title, source, heading = file, "", "Introdução"
    sections: list[Section] = []
    buffer: list[str] = []

    def flush() -> None:
        text = "\n".join(buffer).strip()
        if text:
            sections.append(Section(file, title, source, heading, text))
        buffer.clear()

    for line in markdown.splitlines():
        stripped = line.strip()
        if stripped.startswith("# ") and title == file:
            title = stripped[2:].strip()
        elif stripped.lower().startswith("fonte:") and not source:
            source = stripped.split(":", 1)[1].strip()
        elif stripped.startswith("## "):
            flush()
            heading = stripped[3:].strip()
        else:
            buffer.append(line)
    flush()
    return sections


def load_sections(directory: Path) -> list[Section]:
    sections: list[Section] = []
    for path in sorted(directory.glob("*.md")):
        sections.extend(parse_markdown(path.name, path.read_text(encoding="utf-8")))
    return sections


def chunk_id(file: str, index: int) -> str:
    return str(uuid.uuid5(_NAMESPACE, f"{file}#{index}"))


def split_sections(sections: list[Section], chunk_size: int, chunk_overlap: int) -> list[Chunk]:
    """Divide cada seção com o RecursiveCharacterTextSplitter (800 caracteres, 100 de sobreposição)."""
    from langchain_text_splitters import RecursiveCharacterTextSplitter

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size, chunk_overlap=chunk_overlap, separators=["\n\n", "\n- ", "\n", ". ", " ", ""])
    chunks: list[Chunk] = []
    counters: dict[str, int] = {}
    for section in sections:
        for piece in splitter.split_text(section.text):
            index = counters.get(section.file, 0)
            counters[section.file] = index + 1
            chunks.append(Chunk(chunk_id(section.file, index), section.file, section.title, section.source,
                                section.heading, piece.strip()))
    return chunks


def load_chunks(directory: Path, chunk_size: int, chunk_overlap: int) -> list[Chunk]:
    return split_sections(load_sections(directory), chunk_size, chunk_overlap)
