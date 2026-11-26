from eval.metrics import cost_per_question, estimate_tokens, keyword_coverage, percentile


def test_coverage_ignores_accents_and_case():
    assert keyword_coverage("Até 5 DIAS, refrigerado", ["5 dias", "refriger"]) == 1.0
    assert keyword_coverage("Não sei responder.", ["nao sei"]) == 1.0
    assert keyword_coverage("acima de 60 °C", ["60", "6 horas"]) == 0.5


def test_nearest_rank_percentiles():
    values = list(range(1, 21))  # 1..20
    assert percentile(values, 50) == 10
    assert percentile(values, 95) == 19
    assert percentile([], 95) == 0.0


def test_cost_per_question():
    # 1.000 tokens de entrada a 0,25 + 200 de saída a 1,50 + 20 de embedding a 0,20 (US$ por milhão)
    assert round(cost_per_question(1000, 200, 20, 0.25, 1.50, 0.20), 6) == 0.000554
    assert estimate_tokens("abcd" * 10) == 10
