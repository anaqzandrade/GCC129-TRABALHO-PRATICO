"""Cálculos da avaliação: cobertura das respostas, percentis de latência e custo."""

from __future__ import annotations

import math

from app.textnorm import normalize


def keyword_coverage(answer: str, keywords: list[str]) -> float:
    """Fração das palavras esperadas que aparecem na resposta (sem acento e sem diferenciar maiúsculas)."""
    if not keywords:
        return 1.0
    text = normalize(answer)
    return sum(1 for keyword in keywords if normalize(keyword) in text) / len(keywords)


def percentile(values: list[float], p: float) -> float:
    """Percentil pelo método do posto mais próximo (nearest rank)."""
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, math.ceil(p / 100 * len(ordered)))
    return ordered[rank - 1]


def cost_per_question(input_tokens: float, output_tokens: float, embedding_tokens: float,
                      price_input: float, price_output: float, price_embedding: float) -> float:
    """Custo em US$ de uma pergunta, com preços por 1 milhão de tokens."""
    return (input_tokens * price_input + output_tokens * price_output + embedding_tokens * price_embedding) / 1_000_000


def estimate_tokens(text: str) -> int:
    """Estimativa usada só para o embedding da pergunta, cujo uso o provedor não devolve: ~4 caracteres por token."""
    return max(1, math.ceil(len(text) / 4))
