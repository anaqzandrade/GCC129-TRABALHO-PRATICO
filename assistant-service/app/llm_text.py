"""Leitura do texto e do consumo de tokens das respostas do modelo."""

from __future__ import annotations


def message_text(message) -> str:
    """Texto de um AIMessage. Os modelos Gemini 3 podem devolver o conteúdo como lista de partes."""
    content = getattr(message, "content", message)
    if isinstance(content, str):
        return content.strip()
    parts = []
    for part in content or []:
        if isinstance(part, str):
            parts.append(part)
        elif isinstance(part, dict) and part.get("type", "text") == "text" and isinstance(part.get("text"), str):
            parts.append(part["text"])
    return "".join(parts).strip()


def usage_of(message) -> tuple[int, int]:
    """(tokens de entrada, tokens de saída) do usage_metadata; zero quando o provedor não informa."""
    usage = getattr(message, "usage_metadata", None) or {}
    return int(usage.get("input_tokens", 0) or 0), int(usage.get("output_tokens", 0) or 0)
