"""Unit tests for strategy router endpoint."""
from unittest.mock import AsyncMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from app.clients import get_llm_client
from app.main import create_app


class _StubLLM:
    async def chat(self, messages: list[dict], *, model: str | None = None) -> str:
        return "{}"

    async def chat_structured(self, messages, response_model, *, model=None):
        return response_model.model_validate({})


@pytest.fixture
def app():
    application = create_app()
    application.dependency_overrides[get_llm_client] = _StubLLM
    return application


@pytest.fixture
async def client(app):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


MOCK_FEATURES = {
    "FEAT_001": "enterprise",
    "FEAT_002": "finance",
    "FEAT_003": "korea",
    "FEAT_007": "poc",
    "FEAT_011": True,
    "FEAT_015": "engaged",
    "FEAT_049": "scoping",
    "FEAT_055": [],
    "FEAT_056": ["k_isms"],
}


class TestNextActionsEndpoint:
    @pytest.mark.asyncio
    async def test_returns_200_with_recommendations(self, client):
        with patch(
            "app.routers.strategy.extract_features",
            new_callable=AsyncMock,
            return_value=MOCK_FEATURES,
        ):
            resp = await client.post(
                "/ai/next-actions",
                json={"accountId": 1, "context": "한국 대형 보험사 PoC 진행 중"},
                headers={"X-Tenant-Id": "1"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == 200
        assert isinstance(body["data"], list)
        assert len(body["data"]) > 0

        first = body["data"][0]
        assert "nextActionId" in first
        assert "action" in first
        assert "successProbability" in first
        assert "category" in first
        assert "risk" in first
        assert "recommendedScript" in first
        assert "sources" in first
        assert first["priority"] == 1

    @pytest.mark.asyncio
    async def test_missing_context_returns_400(self, client):
        resp = await client.post(
            "/ai/next-actions",
            json={"accountId": 1, "context": ""},
            headers={"X-Tenant-Id": "1"},
        )
        assert resp.status_code == 400

    @pytest.mark.asyncio
    async def test_missing_tenant_returns_401(self, client):
        resp = await client.post(
            "/ai/next-actions",
            json={"accountId": 1, "context": "테스트 상황"},
        )
        assert resp.status_code == 401

    @pytest.mark.asyncio
    async def test_feature_extraction_failure_returns_502(self, client):
        with patch(
            "app.routers.strategy.extract_features",
            new_callable=AsyncMock,
            return_value={},
        ):
            resp = await client.post(
                "/ai/next-actions",
                json={"accountId": 1, "context": "상황 설명"},
                headers={"X-Tenant-Id": "1"},
            )
        assert resp.status_code == 502

    @pytest.mark.asyncio
    async def test_response_fields_structure(self, client):
        with patch(
            "app.routers.strategy.extract_features",
            new_callable=AsyncMock,
            return_value=MOCK_FEATURES,
        ):
            resp = await client.post(
                "/ai/next-actions",
                json={"accountId": 42, "context": "한국 대형 보험사 PoC scoping 단계"},
                headers={"X-Tenant-Id": "1"},
            )
        body = resp.json()
        item = body["data"][0]
        assert item["accountId"] == 42
        assert isinstance(item["successProbability"], int)
        assert 0 <= item["successProbability"] <= 99
        assert "sources" in item
        assert "news" in item["sources"]
        assert "dart" in item["sources"]
        assert "crm" in item["sources"]
