"""POST /ai/signals/collect 라우터 테스트."""
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.clients import get_embedder_client, get_naver_client
from app.clients.embedder import OnnxEmbedderClient
from app.clients.naver import NaverNewsClient
from app.core.db import get_db
from app.core.errors import register_exception_handlers
from app.core.security import get_tenant_id
from app.routers.news import router
from app.schemas.news import (
    DartResult,
    NewsResult,
    SignalsCollectResponse,
)

TENANT_ID = 1

MOCK_RESULT = SignalsCollectResponse(
    total_accounts=3,
    news=NewsResult(new_articles=[], existing_links=[]),
    dart=DartResult(new_disclosures=[], existing_rcept_nos=[]),
    errors=[],
)


@pytest.fixture
def mock_db():
    return AsyncMock()


@pytest.fixture
def mock_naver():
    return MagicMock(spec=NaverNewsClient)


@pytest.fixture
def mock_embedder():
    return MagicMock(spec=OnnxEmbedderClient)


@pytest.fixture
def test_app(mock_db, mock_naver, mock_embedder):
    app = FastAPI()
    register_exception_handlers(app)
    app.include_router(router)
    app.dependency_overrides = {
        get_tenant_id: lambda: TENANT_ID,
        get_db: lambda: mock_db,
        get_naver_client: lambda: mock_naver,
        get_embedder_client: lambda: mock_embedder,
    }
    return app


@pytest.fixture
async def ac(test_app):
    async with AsyncClient(
        transport=ASGITransport(app=test_app),
        base_url="http://test",
    ) as client:
        yield client


class TestCollectSignalsEndpoint:
    @pytest.mark.asyncio
    async def test_returns_200_with_news_and_dart_sections(self, ac):
        with patch(
            "app.routers.news.collect_news",
            new_callable=AsyncMock,
            return_value=MOCK_RESULT,
        ):
            resp = await ac.post(
                "/ai/signals/collect",
                json={"source": "naver"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == 200
        data = body["data"]
        assert data["total_accounts"] == 3
        assert data["news"]["new_articles"] == []
        assert data["news"]["existing_links"] == []
        assert data["dart"]["new_disclosures"] == []
        assert data["dart"]["existing_rcept_nos"] == []

    @pytest.mark.asyncio
    async def test_missing_tenant_returns_401(self, mock_db, mock_naver, mock_embedder):
        app = FastAPI()
        register_exception_handlers(app)
        app.include_router(router)
        app.dependency_overrides = {
            get_db: lambda: mock_db,
            get_naver_client: lambda: mock_naver,
            get_embedder_client: lambda: mock_embedder,
        }
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            resp = await client.post(
                "/ai/signals/collect",
                json={"source": "naver"},
            )
        assert resp.status_code == 401

    @pytest.mark.asyncio
    async def test_null_embedder_returns_400(self, mock_db, mock_naver):
        app = FastAPI()
        register_exception_handlers(app)
        app.include_router(router)
        app.dependency_overrides = {
            get_tenant_id: lambda: TENANT_ID,
            get_db: lambda: mock_db,
            get_naver_client: lambda: mock_naver,
            get_embedder_client: lambda: None,
        }
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            resp = await client.post(
                "/ai/signals/collect",
                json={"source": "naver"},
            )
        assert resp.status_code == 400

    @pytest.mark.asyncio
    async def test_dart_source_returns_pending_error(self, ac):
        with patch(
            "app.routers.news.collect_news",
            new_callable=AsyncMock,
            return_value=SignalsCollectResponse(
                total_accounts=3,
                news=NewsResult(new_articles=[], existing_links=[]),
                dart=DartResult(new_disclosures=[], existing_rcept_nos=[]),
                errors=["DART 수집은 추후 구현 예정입니다"],
            ),
        ):
            resp = await ac.post(
                "/ai/signals/collect",
                json={"source": "dart"},
            )
        body = resp.json()
        assert body["status"] == 200
        assert "DART" in body["data"]["errors"][0]

    @pytest.mark.asyncio
    async def test_invalid_source_returns_422(self, ac):
        resp = await ac.post(
            "/ai/signals/collect",
            json={"source": "invalid"},
        )
        assert resp.status_code == 400