"""Regras das tools que não dependem de rede: categoria em português e filtros das respostas dos serviços."""

from __future__ import annotations

from app.textnorm import normalize

CATEGORIES = {
    "VEGETABLES": "hortaliças",
    "FRUITS": "frutas",
    "GRAINS": "grãos e secos",
    "BAKERY": "padaria",
    "DAIRY": "laticínios",
    "PREPARED_FOOD": "refeições prontas",
    "OTHER": "outros",
}

_ALIASES = {
    "VEGETABLES": ["hortalica", "hortalicas", "verdura", "verduras", "legume", "legumes", "vegetal", "vegetais",
                   "folhosas", "vegetables"],
    "FRUITS": ["fruta", "frutas", "fruits"],
    "GRAINS": ["grao", "graos", "graos e secos", "secos", "cereais", "arroz", "feijao", "massas", "farinha",
               "grains"],
    "BAKERY": ["padaria", "pao", "paes", "panificados", "bolo", "bolos", "bakery"],
    "DAIRY": ["laticinio", "laticinios", "leite", "queijo", "queijos", "iogurte", "iogurtes", "derivados de leite",
              "dairy"],
    "PREPARED_FOOD": ["refeicao", "refeicoes", "refeicao pronta", "refeicoes prontas", "comida pronta",
                      "comida preparada", "marmita", "marmitas", "preparados", "prepared food", "prepared_food"],
    "OTHER": ["outro", "outros", "other"],
}
_LOOKUP = {alias: category for category, aliases in _ALIASES.items() for alias in aliases}


def normalize_category(value: str | None) -> str | None:
    """"laticínios", "Laticinios" ou "DAIRY" viram DAIRY; texto desconhecido vira None."""
    if not value:
        return None
    text = normalize(value).replace("-", " ")
    if text.upper().replace(" ", "_") in CATEGORIES:
        return text.upper().replace(" ", "_")
    return _LOOKUP.get(text)


def _same_city(a: str | None, b: str | None) -> bool:
    return normalize(a or "") == normalize(b or "")


def summarize_offers(page: dict, limit: int = 10) -> dict:
    """Resumo da página do modelo de leitura (GET /api/v1/offers/available) para o modelo."""
    items = page.get("items", []) if isinstance(page, dict) else []
    offers = [{
        "oferta": item.get("description"),
        "categoria": item.get("foodCategory"),
        "quantidade": item.get("quantity"),
        "unidade": item.get("unit"),
        "cidade": item.get("city"),
        "doador": item.get("donorName"),
        "disponivelAte": item.get("availableUntil"),
    } for item in items[:limit]]
    return {"total": page.get("total", len(items)) if isinstance(page, dict) else 0, "ofertas": offers}


def institutions_accepting(page: dict, category: str, city: str | None = None, limit: int = 10) -> dict:
    """Instituições ativas cuja capacidade de recebimento inclui a categoria (GET /api/v1/organizations)."""
    content = page.get("content", page.get("items", [])) if isinstance(page, dict) else []
    found = []
    for organization in content:
        if organization.get("type") not in (None, "INSTITUTION") or organization.get("active") is False:
            continue
        address = organization.get("address") or {}
        if city and not _same_city(address.get("city"), city):
            continue
        for capacity in organization.get("receivingCapacity") or []:
            if (capacity.get("foodCategory") or "").upper() == category:
                found.append({
                    "instituicao": organization.get("name"),
                    "cidade": address.get("city"),
                    "bairro": address.get("district"),
                    "capacidadeMaxima": capacity.get("maxQuantity"),
                    "unidade": capacity.get("unit"),
                })
                break
    return {"categoria": category, "total": len(found), "instituicoes": found[:limit]}
