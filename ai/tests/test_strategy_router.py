"""Unit tests for strategy router endpoint."""
from unittest.mock import AsyncMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from app.clients import get_llm_client
from app.core.db import get_db
from app.main import create_app
from app.strategy.context_builder import ContextData


class _StubLLM:
    async def chat(self, messages: list[dict], *, model: str | None = None) -> str:
        return "{}"

    async def chat_structured(self, messages, response_model, *, model=None):
        return response_model.model_validate({})

    async def chat_with_tools(self, messages, tools, *, model=None):
        return None


async def _mock_db():
    yield None


_VALID_BODY = {
    "account_id": 1,
    "trigger_type": "NEWS_UPDATED",
    "context": "한국 대형 보험사 PoC 진행 중",
}

_MOCK_CONTEXT = ContextData(
    account_name="테스트기업",
    account_industry="IT",
    news_items=[],
    dart_items=[],
    meetings=[],
    context_text="[고객사 정보]\n회사명: 테스트기업\n업종: IT\n\n[추가 컨텍스트]\n한국 대형 보험사 PoC 진행 중",
)

_EMPTY_CONTEXT = ContextData(
    account_name="테스트기업",
    account_industry="IT",
    context_text="",
)


@pytest.fixture
def app():
    application = create_app()
    application.dependency_overrides[get_llm_client] = _StubLLM
    application.dependency_overrides[get_db] = _mock_db
    return application


@pytest.fixture
async def client(app):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


class TestNextActionsEndpoint:
    @pytest.mark.asyncio
    async def test_returns_200_with_recommendations(self, client):
        with (
            patch("app.routers.strategy.build_context", new_callable=AsyncMock, return_value=_MOCK_CONTEXT),
            patch("app.routers.strategy.load_pipeline_stages", new_callable=AsyncMock, return_value={}),
        ):
            resp = await client.post(
                "/api/v1/ai/next-actions",
                json=_VALID_BODY,
                headers={"X-Tenant-Id": "1"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert isinstance(body, list)
        assert len(body) > 0

        first = body[0]
        assert "action" in first
        assert "reason" in first
        assert "category" in first
        assert "success_probability" in first
        assert "importance_score" in first
        assert "recommended_script" in first
        assert "sources" in first
        assert "pipeline_stage_id" in first

    @pytest.mark.asyncio
    async def test_importance_score_is_0_to_5(self, client):
        with (
            patch("app.routers.strategy.build_context", new_callable=AsyncMock, return_value=_MOCK_CONTEXT),
            patch("app.routers.strategy.load_pipeline_stages", new_callable=AsyncMock, return_value={}),
        ):
            resp = await client.post(
                "/api/v1/ai/next-actions",
                json=_VALID_BODY,
                headers={"X-Tenant-Id": "1"},
            )
        body = resp.json()
        for item in body:
            assert isinstance(item["importance_score"], int)
            assert 0 <= item["importance_score"] <= 5

    @pytest.mark.asyncio
    async def test_empty_context_returns_400(self, client):
        with (
            patch("app.routers.strategy.build_context", new_callable=AsyncMock, return_value=_EMPTY_CONTEXT),
            patch("app.routers.strategy.load_pipeline_stages", new_callable=AsyncMock, return_value={}),
        ):
            resp = await client.post(
                "/api/v1/ai/next-actions",
                json=_VALID_BODY,
                headers={"X-Tenant-Id": "1"},
            )
        assert resp.status_code == 400

    @pytest.mark.asyncio
    async def test_missing_tenant_returns_401(self, client):
        resp = await client.post(
            "/api/v1/ai/next-actions",
            json=_VALID_BODY,
        )
        assert resp.status_code == 401

    @pytest.mark.asyncio
    async def test_feature_extraction_failure_returns_502(self, client):
        with (
            patch("app.routers.strategy.build_context", new_callable=AsyncMock, return_value=_MOCK_CONTEXT),
            patch("app.routers.strategy.extract_features", new_callable=AsyncMock, return_value={}),
            patch("app.routers.strategy.load_pipeline_stages", new_callable=AsyncMock, return_value={}),
        ):
            resp = await client.post(
                "/api/v1/ai/next-actions",
                json=_VALID_BODY,
                headers={"X-Tenant-Id": "1"},
            )
        assert resp.status_code == 502

    @pytest.mark.asyncio
    async def test_response_fields_structure(self, client):
        with (
            patch("app.routers.strategy.build_context", new_callable=AsyncMock, return_value=_MOCK_CONTEXT),
            patch("app.routers.strategy.load_pipeline_stages", new_callable=AsyncMock, return_value={}),
        ):
            resp = await client.post(
                "/api/v1/ai/next-actions",
                json={**_VALID_BODY, "account_id": 42},
                headers={"X-Tenant-Id": "1"},
            )
        body = resp.json()
        assert isinstance(body, list)
        assert len(body) > 0

        item = body[0]
        assert isinstance(item["success_probability"], int)
        assert 0 <= item["success_probability"] <= 99
        assert isinstance(item["importance_score"], int)
        assert 0 <= item["importance_score"] <= 5
        assert "sources" in item
        assert "news" in item["sources"]
        assert "dart" in item["sources"]
        assert "crm" in item["sources"]
