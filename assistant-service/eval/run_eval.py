"""Avaliação do RAG, latência e custo do assistente (doc 07, seção 8).

Rodar dentro do container, com o serviço no ar:

    docker compose exec assistant-service python -m eval.run_eval
    docker compose cp assistant-service:/tmp/avaliacao-llm.md .\\avaliacao-llm.md

1. Cada pergunta de eval/questions.json é feita com e sem recuperação (useRetrieval true/false).
2. A latência é medida em N chamadas seguidas (padrão 20), com recuperação.
3. O custo sai dos tokens de entrada e saída de cada resposta (usage_metadata) vezes o preço do nível pago.

No nível gratuito do Gemini o limite é por minuto; a pausa entre chamadas (--pause) evita respostas 429,
que virariam fallback e distorceriam a avaliação. Com o nível pago, use --pause 0.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from eval.metrics import cost_per_question, estimate_tokens, keyword_coverage, percentile

HERE = Path(__file__).resolve().parent


def ask(base_url: str, question: str, use_retrieval: bool, correlation_id: str) -> tuple[dict, float]:
    body = json.dumps({"question": question, "useRetrieval": use_retrieval}).encode("utf-8")
    request = urllib.request.Request(f"{base_url}/api/v1/assistant/questions", data=body, method="POST",
                                     headers={"Content-Type": "application/json", "X-Correlation-ID": correlation_id})
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        payload = {"mode": "error", "answer": error.read().decode("utf-8"), "sources": [], "toolCalls": [],
                   "usage": {"inputTokens": 0, "outputTokens": 0, "llmCalls": 0}, "latencyMs": 0}
    return payload, (time.monotonic() - started) * 1000


def cell(text: str, limit: int = 0) -> str:
    text = " ".join(str(text).split()).replace("|", "\\|")
    return text if not limit or len(text) <= limit else text[: limit - 1] + "…"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-url", default=f"http://localhost:{os.getenv('SERVER_PORT', '8095')}")
    parser.add_argument("--repeat", type=int, default=20, help="chamadas da medição de latência")
    parser.add_argument("--pause", type=float, default=8.0, help="segundos entre chamadas (nível gratuito: 8)")
    parser.add_argument("--price-input", type=float, default=float(os.getenv("PRICE_INPUT_PER_MTOK", "0.25")))
    parser.add_argument("--price-output", type=float, default=float(os.getenv("PRICE_OUTPUT_PER_MTOK", "1.50")))
    parser.add_argument("--price-embedding", type=float, default=float(os.getenv("PRICE_EMBEDDING_PER_MTOK", "0.20")))
    parser.add_argument("--monthly", type=int, nargs="+", default=[10_000, 100_000])
    parser.add_argument("--output", default="/tmp/avaliacao-llm.md")
    args = parser.parse_args(argv)
    questions = json.loads((HERE / "questions.json").read_text(encoding="utf-8"))
    model = os.getenv("GEMINI_CHAT_MODEL", "gemini-3.1-flash-lite")
    lines: list[str] = []

    def out(text: str = "") -> None:
        print(text, flush=True)
        lines.append(text)

    # ---------------------------------------------------------------- 1. com e sem recuperação
    rows = []
    for item in questions:
        for use_retrieval in (True, False):
            result, client_ms = ask(args.base_url, item["question"], use_retrieval,
                                    f"eval-{item['id']}-{'rag' if use_retrieval else 'sem'}")
            rows.append((item, use_retrieval, result, client_ms))
            print(f"{item['id']} {'com' if use_retrieval else 'sem'} RAG: mode={result.get('mode')} "
                  f"{int(client_ms)} ms", file=sys.stderr, flush=True)
            time.sleep(args.pause)

    out(f"## Avaliação do RAG ({model}, {time.strftime('%d/%m/%Y %H:%M')})")
    out()
    out("Cobertura: fração das informações esperadas (palavras-chave de eval/questions.json) presentes na resposta.")
    out()
    out("| Pergunta | Esperado | Cobertura com RAG | Cobertura sem RAG | Fontes citadas (com RAG) | Tools | Modo |")
    out("|---|---|---|---|---|---|---|")
    by_id: dict = {}
    for item, use_retrieval, result, _ in rows:
        by_id.setdefault(item["id"], {})["rag" if use_retrieval else "plain"] = result
    totals = {"rag": [], "plain": []}
    for item in questions:
        rag, plain = by_id[item["id"]]["rag"], by_id[item["id"]]["plain"]
        coverage_rag = keyword_coverage(rag.get("answer", ""), item["keywords"])
        coverage_plain = keyword_coverage(plain.get("answer", ""), item["keywords"])
        totals["rag"].append(coverage_rag)
        totals["plain"].append(coverage_plain)
        refs = {int(number) for group in re.findall(r"\[([\d,\s]+)\]", rag.get("answer", ""))
                for number in re.findall(r"\d+", group)}  # aceita [1] e [1, 2]
        cited = sorted({source["title"] for source in rag.get("sources", []) if source["ref"] in refs})
        tools = ", ".join(sorted({call["name"] for call in rag.get("toolCalls", [])})) or "—"
        if item.get("expectedTool"):
            tools += " ✓" if item["expectedTool"] in tools else f" (esperada: {item['expectedTool']})"
        out(f"| {item['id']}. {cell(item['question'])} | {cell(item['expected'])} | {coverage_rag:.0%} | "
            f"{coverage_plain:.0%} | {cell('; '.join(cited) or '—')} | {tools} | "
            f"{rag.get('mode')} / {plain.get('mode')} |")
    out()
    out(f"Média de cobertura: **{statistics.mean(totals['rag']):.0%} com RAG** e "
        f"**{statistics.mean(totals['plain']):.0%} sem RAG**.")
    out()
    out("### Respostas")
    for item in questions:
        out()
        out(f"**{item['id']}. {item['question']}**")
        for key, label in (("rag", "Com RAG"), ("plain", "Sem RAG")):
            answer = by_id[item["id"]][key].get("answer", "")
            out(f"- {label}: {cell(answer, 700)}")

    # ---------------------------------------------------------------- 2. latência
    latencies, server_latencies, tokens_in, tokens_out, embedding_tokens, modes = [], [], [], [], [], []
    for position in range(args.repeat):
        item = questions[position % len(questions)]
        result, client_ms = ask(args.base_url, item["question"], True, f"eval-latencia-{position + 1}")
        latencies.append(client_ms)
        server_latencies.append(result.get("latencyMs", 0))
        modes.append(result.get("mode"))
        if result.get("mode") == "llm":
            tokens_in.append(result["usage"]["inputTokens"])
            tokens_out.append(result["usage"]["outputTokens"])
            embedding_tokens.append(estimate_tokens(item["question"]))
        print(f"latência {position + 1}/{args.repeat}: {int(client_ms)} ms ({result.get('mode')})",
              file=sys.stderr, flush=True)
        time.sleep(args.pause)

    out()
    out(f"## Latência ({args.repeat} chamadas seguidas, com recuperação)")
    out()
    out("| Medida | p50 | p95 | Média | Máximo |")
    out("|---|---|---|---|---|")
    for label, values in (("Cliente (HTTP, ms)", latencies), ("Servidor (latencyMs)", server_latencies)):
        out(f"| {label} | {percentile(values, 50):.0f} | {percentile(values, 95):.0f} | "
            f"{statistics.mean(values):.0f} | {max(values):.0f} |")
    out()
    out(f"Respostas pelo modelo: {modes.count('llm')} de {len(modes)}; pelo fallback: {modes.count('fallback')}.")

    # ---------------------------------------------------------------- 3. custo
    out()
    out("## Custo")
    out()
    if not tokens_in:
        out("Nenhuma resposta veio do modelo; sem tokens para calcular o custo.")
    else:
        avg_in, avg_out = statistics.mean(tokens_in), statistics.mean(tokens_out)
        avg_embedding = statistics.mean(embedding_tokens)
        cost = cost_per_question(avg_in, avg_out, avg_embedding, args.price_input, args.price_output,
                                 args.price_embedding)
        out(f"Preços do nível pago (US$ por 1 milhão de tokens): entrada {args.price_input}, saída "
            f"{args.price_output}, embedding {args.price_embedding}.")
        out()
        out("| Medida | Valor |")
        out("|---|---|")
        out(f"| Tokens de entrada por pergunta (média) | {avg_in:.0f} |")
        out(f"| Tokens de saída por pergunta (média) | {avg_out:.0f} |")
        out(f"| Tokens do embedding da pergunta (estimativa) | {avg_embedding:.0f} |")
        out(f"| Custo por pergunta | US$ {cost:.6f} |")
        for volume in args.monthly:
            out(f"| {volume:,} perguntas por mês | US$ {cost * volume:.2f} |".replace(",", "."))
    Path(args.output).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\nResultado gravado em {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
