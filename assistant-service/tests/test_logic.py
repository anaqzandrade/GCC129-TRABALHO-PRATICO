import pytest

from app.errors import describe, is_retryable, status_code
from app.fallback import fallback_answer
from app.knowledge import Chunk
from app.llm_text import message_text, usage_of
from app.tool_logic import institutions_accepting, normalize_category, summarize_offers


class ProviderError(Exception):
    def __init__(self, message, code=None):
        super().__init__(message)
        self.code = code


@pytest.mark.parametrize("error, expected", [
    (TimeoutError("sem resposta"), True),
    (ProviderError("quota", code=429), True),
    (ProviderError("server", code=503), True),
    (ProviderError("API key not valid", code=400), False),
    (ProviderError("permission", code=403), False),
    (Exception("429 RESOURCE_EXHAUSTED: quota"), True),
    (Exception("400 INVALID_ARGUMENT: API key not valid. Please pass a valid API key."), False),
    (ValueError("schema inválido"), False),
])
def test_only_transient_failures_are_retried(error, expected):
    assert is_retryable(error) is expected


def test_cause_chain_is_inspected():
    try:
        try:
            raise ProviderError("limite", code=429)
        except ProviderError as inner:
            raise RuntimeError("falha no wrapper") from inner
    except RuntimeError as outer:
        assert status_code(outer) == 429
        assert is_retryable(outer)
        assert describe(outer).startswith("RuntimeError 429")


@pytest.mark.parametrize("text, expected", [
    ("laticínios", "DAIRY"), ("Laticinios", "DAIRY"), ("DAIRY", "DAIRY"), ("hortaliças", "VEGETABLES"),
    ("refeições prontas", "PREPARED_FOOD"), ("prepared_food", "PREPARED_FOOD"), ("pães", "BAKERY"),
    ("parafusos", None), (None, None),
])
def test_category_in_portuguese(text, expected):
    assert normalize_category(text) == expected


def test_institutions_are_filtered_by_capacity_and_city():
    page = {"content": [
        {"name": "Casa A", "type": "INSTITUTION", "active": True, "document": "123",
         "address": {"city": "Lavras", "district": "Centro"},
         "receivingCapacity": [{"foodCategory": "DAIRY", "maxQuantity": 20, "unit": "L"}]},
        {"name": "Casa B", "type": "INSTITUTION", "active": True, "address": {"city": "Perdões"},
         "receivingCapacity": [{"foodCategory": "DAIRY", "maxQuantity": 5, "unit": "L"}]},
        {"name": "Casa C", "type": "INSTITUTION", "active": True, "address": {"city": "Lavras"},
         "receivingCapacity": [{"foodCategory": "VEGETABLES", "maxQuantity": 50, "unit": "KG"}]},
    ]}
    result = institutions_accepting(page, "DAIRY", "lavras")
    assert result["total"] == 1
    assert result["instituicoes"][0] == {"instituicao": "Casa A", "cidade": "Lavras", "bairro": "Centro",
                                         "capacidadeMaxima": 20, "unidade": "L"}
    assert "document" not in str(result)  # o CNPJ não vai para o modelo


def test_offers_summary_keeps_only_what_the_model_needs():
    page = {"items": [{"id": "offer-1", "description": "Iogurte", "foodCategory": "DAIRY", "quantity": 20,
                       "unit": "UNIT", "city": "Lavras", "donorName": "Mercado", "lastEvent": "OfferCreated"}],
            "total": 1}
    summary = summarize_offers(page)
    assert summary["total"] == 1
    assert summary["ofertas"][0]["doador"] == "Mercado"
    assert "lastEvent" not in summary["ofertas"][0]


class FakeMessage:
    def __init__(self, content, usage=None):
        self.content = content
        self.usage_metadata = usage


def test_text_from_string_or_parts():
    assert message_text(FakeMessage("  olá ")) == "olá"
    parts = [{"type": "thinking", "thinking": "..."}, {"type": "text", "text": "Pode doar"}, " sim."]
    assert message_text(FakeMessage(parts)) == "Pode doar sim."
    assert usage_of(FakeMessage("x", {"input_tokens": 120, "output_tokens": 30})) == (120, 30)
    assert usage_of(FakeMessage("x")) == (0, 0)


def test_fallback_lists_the_chunks_with_their_source():
    chunk = Chunk("1", "rdc.md", "RDC 216/2004", "Anvisa", "Conservação", "Acima de 60 °C por no máximo 6 horas.")
    answer, notice = fallback_answer([chunk], "circuito aberto")
    assert "indisponível" in notice and "circuito aberto" in notice
    assert "[1] RDC 216/2004 — Conservação" in answer and "6 horas" in answer
    empty, _ = fallback_answer([], "sem chave")
    assert "Não encontrei trechos" in empty
