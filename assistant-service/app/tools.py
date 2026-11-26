"""Tools que deixam o modelo consultar dados reais da Ravita, pelas APIs dos serviços (nunca pelos bancos)."""

from __future__ import annotations

import logging
from typing import Optional

import httpx
from langchain_core.tools import tool

from app.config import Settings
from app.context import CORRELATION_ID
from app.tool_logic import CATEGORIES, institutions_accepting, normalize_category, summarize_offers

log = logging.getLogger("app.tools")
_VALID = ", ".join(f"{name} ({label})" for name, label in CATEGORIES.items())


class SystemClient:
    def __init__(self, settings: Settings) -> None:
        self.offer_url = settings.offer_url.rstrip("/")
        self.organization_url = settings.organization_url.rstrip("/")
        self.timeout = settings.tool_timeout

    def _get(self, url: str, params: dict) -> dict:
        headers = {"X-Correlation-ID": CORRELATION_ID.get()} if CORRELATION_ID.get() != "-" else {}
        response = httpx.get(url, params=params, headers=headers, timeout=self.timeout)
        response.raise_for_status()
        return response.json()

    def available_offers(self, category: Optional[str], city: Optional[str]) -> dict:
        code = normalize_category(category)
        if category and code is None:
            return {"erro": f"Categoria desconhecida: {category}.", "categoriasValidas": _VALID}
        params = {"size": 20}
        if code:
            params["category"] = code
        if city:
            params["city"] = city
        try:
            return summarize_offers(self._get(f"{self.offer_url}/api/v1/offers/available", params))
        except httpx.HTTPError as error:
            log.warning("Tool buscar_ofertas_disponiveis falhou: %s", error)
            return {"erro": "Não foi possível consultar as ofertas agora."}

    def institutions(self, category: str, city: Optional[str]) -> dict:
        code = normalize_category(category)
        if code is None:
            return {"erro": f"Categoria desconhecida: {category}.", "categoriasValidas": _VALID}
        try:
            page = self._get(f"{self.organization_url}/api/v1/organizations",
                             {"type": "INSTITUTION", "active": "true", "size": 100})
        except httpx.HTTPError as error:
            log.warning("Tool instituicoes_que_aceitam falhou: %s", error)
            return {"erro": "Não foi possível consultar as instituições agora."}
        return institutions_accepting(page, code, city)


def build_tools(settings: Settings) -> list:
    client = SystemClient(settings)

    @tool
    def buscar_ofertas_disponiveis(categoria: Optional[str] = None, cidade: Optional[str] = None) -> dict:
        """Lista as ofertas de alimentos disponíveis agora na Ravita (modelo de leitura do offer-service).

        Use quando perguntarem o que há disponível para receber.

        Args:
            categoria: hortaliças, frutas, grãos, padaria, laticínios, refeições prontas ou outros. Opcional.
            cidade: nome da cidade de coleta, por exemplo Lavras. Opcional.
        """
        return client.available_offers(categoria, cidade)

    @tool
    def instituicoes_que_aceitam(categoria: str, cidade: Optional[str] = None) -> dict:
        """Lista as instituições ativas da Ravita que recebem a categoria informada, com a capacidade declarada.

        Use quando perguntarem para quem doar um alimento.

        Args:
            categoria: hortaliças, frutas, grãos, padaria, laticínios, refeições prontas ou outros.
            cidade: nome da cidade da instituição, por exemplo Lavras. Opcional.
        """
        return client.institutions(categoria, cidade)

    return [buscar_ofertas_disponiveis, instituicoes_que_aceitam]
