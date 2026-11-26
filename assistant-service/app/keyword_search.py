"""Busca por palavras-chave sobre os mesmos trechos da base vetorial.

É a recuperação do fallback: não depende do provedor de embeddings. Pontua cada trecho pela soma do
IDF dos termos da pergunta que aparecem nele, com um radical simples para plural e gênero.
"""

from __future__ import annotations

import math
from collections import Counter

from app.textnorm import words

_STOPWORDS = {
    "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "no", "na", "nos",
    "nas", "por", "para", "pra", "com", "sem", "sob", "que", "qual", "quais", "quando", "como", "onde", "quem",
    "e", "ou", "se", "ao", "aos", "mais", "menos", "muito", "pode", "posso", "podem", "devo", "deve", "ser",
    "esta", "estao", "isso", "essa", "esse", "este", "meu", "minha", "seu", "sua", "tem", "ter", "ha", "hoje",
    "ainda", "ja", "nao", "sim", "vale", "fazer", "faz", "sobre", "ate", "apos", "entre", "the", "is",
}


def stem(token: str) -> str:
    """Radical bem simples: tira plural e a vogal final, para "laticínios" casar com "laticínio"."""
    if token.isdigit() or len(token) <= 3:
        return token
    for suffix in ("coes", "cao", "oes", "es", "s"):
        if token.endswith(suffix) and len(token) - len(suffix) >= 3:
            token = token[: -len(suffix)] + ("ca" if suffix in ("coes", "cao") else "")
            break
    if len(token) > 4 and token[-1] in "aeo":
        token = token[:-1]
    return token


def terms(text: str) -> list[str]:
    return [stem(word) for word in words(text) if word not in _STOPWORDS]


class KeywordIndex:
    def __init__(self, chunks: list) -> None:
        self.chunks = list(chunks)
        self._terms = [Counter(terms(chunk.content)) for chunk in self.chunks]
        document_frequency: Counter = Counter()
        for counter in self._terms:
            document_frequency.update(counter.keys())
        total = max(len(self.chunks), 1)
        self._idf = {term: math.log(1 + total / df) for term, df in document_frequency.items()}

    def __len__(self) -> int:
        return len(self.chunks)

    def search(self, question: str, k: int = 4) -> list:
        query = set(terms(question))
        if not query:
            return []
        scored = []
        for position, counter in enumerate(self._terms):
            score = sum(self._idf[term] * (1 + math.log(counter[term])) for term in query if term in counter)
            if score > 0:
                scored.append((score, position))
        scored.sort(key=lambda item: (-item[0], item[1]))
        return [self.chunks[position] for _, position in scored[:k]]
