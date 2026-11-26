"""Resposta de fallback: o que o usuário recebe quando o modelo não pode responder."""

from __future__ import annotations


def sources_of(chunks: list) -> list[dict]:
    return [{"ref": position, "title": chunk.title, "section": chunk.section, "source": chunk.source,
             "file": chunk.file} for position, chunk in enumerate(chunks, start=1)]


def excerpt(text: str, limit: int = 280) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 3].rsplit(" ", 1)[0] + "..."


def fallback_answer(chunks: list, reason: str) -> tuple[str, str]:
    """(resposta, aviso). Com trechos, devolve os trechos com a fonte; sem trechos, só o aviso."""
    notice = f"O assistente de IA está indisponível no momento ({reason})."
    if not chunks:
        return (notice + " Não encontrei trechos da base de conhecimento sobre a pergunta. "
                "Tente de novo em alguns instantes ou fale com a equipe da Ravita."), notice
    lines = [notice, "Enquanto isso, estes trechos da base de conhecimento da Ravita tratam do assunto:", ""]
    for position, chunk in enumerate(chunks, start=1):
        lines.append(f"[{position}] {chunk.title} — {chunk.section}: {excerpt(chunk.text)}")
    lines += ["", "Para ver ofertas e instituições em tempo real, use as telas do portal."]
    return "\n".join(lines), notice
