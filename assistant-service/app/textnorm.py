"""Normalização de texto em português, sem dependências: minúsculas e sem acentos."""

import re
import unicodedata

_WORD = re.compile(r"[a-z0-9]+(?:[.,][0-9]+)?")


def strip_accents(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch))


def normalize(text: str) -> str:
    return strip_accents(text or "").lower().strip()


def words(text: str) -> list[str]:
    return _WORD.findall(normalize(text))
