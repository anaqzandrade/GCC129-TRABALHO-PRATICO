"""Contrato HTTP do assistant-service, sem chave e sem base vetorial."""

import pytest

pytest.importorskip("fastapi")
pytest.importorskip("httpx")

from fastapi.testclient import TestClient  # noqa: E402

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402


@pytest.fixture()
def client():
    with TestClient(create_app(Settings(api_key="", vector_enabled=False))) as test_client:
        yield test_client


def test_question_is_answered_by_the_fallback_without_key(client):
    response = client.post("/api/v1/assistant/questions", json={"question": "Refeição quente pode ficar quanto tempo?"},
                           headers={"X-Correlation-ID": "teste-1"})
    assert response.status_code == 200
    assert response.headers["X-Correlation-ID"] == "teste-1"
    body = response.json()
    assert body["mode"] == "fallback" and body["sources"] and body["latencyMs"] >= 0


@pytest.mark.parametrize("payload", [{"question": ""}, {"question": "ok?", "extra": 1}, {}])
def test_invalid_request_uses_the_contract_error(client, payload):
    response = client.post("/api/v1/assistant/questions", json=payload, headers={"X-Correlation-ID": "teste-2"})
    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_REQUEST"
    assert response.json()["correlationId"] == "teste-2"


def test_health(client):
    body = client.get("/health").json()
    assert body["status"] == "UP"
    assert body["llmConfigured"] is False
    assert body["vectorStore"]["status"] == "disabled"
    assert body["circuitBreaker"] == "closed"
