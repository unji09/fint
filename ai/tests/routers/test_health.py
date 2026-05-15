from unittest.mock import AsyncMock

import pytest
import pytest_asyncio
from httpx import (
    ASGITransport,
    AsyncClient,
)

from app.core.db import get_db
from app.core.redis import get_redis
from app.main import create_app


@pytest_asyncio.fixture
async def client() -> AsyncClient:
    app = create_app()

    transport = ASGITransport(app=app)

    async with AsyncClient(
        transport=transport,
        base_url="http://test",
    ) as ac:
        yield ac


@pytest.mark.asyncio
async def test_health_returns_ok(
    client: AsyncClient,
) -> None:
    response = await client.get("/health")

    assert response.status_code == 200

    assert response.json() == {
        "status": "ok",
    }


@pytest.mark.asyncio
async def test_readiness_returns_ready_with_components() -> None:
    app = create_app()

    mock_db = AsyncMock()
    mock_redis = AsyncMock()

    async def _fake_db():
        yield mock_db

    async def _fake_redis():
        yield mock_redis

    app.dependency_overrides[get_db] = _fake_db
    app.dependency_overrides[get_redis] = _fake_redis

    transport = ASGITransport(app=app)

    async with AsyncClient(
        transport=transport,
        base_url="http://test",
    ) as client:
        resp = await client.get("/health/ready")

    assert resp.status_code == 200

    body = resp.json()
    assert body["status"] == "ready"
    assert body["db"] == "ok"
    assert body["redis"] == "ok"

    mock_db.execute.assert_called_once()
    mock_redis.ping.assert_called_once()


@pytest.mark.asyncio
async def test_readiness_503_when_db_down() -> None:
    app = create_app()

    mock_db = AsyncMock()
    mock_db.execute.side_effect = Exception("connection refused")
    mock_redis = AsyncMock()

    async def _fake_db():
        yield mock_db

    async def _fake_redis():
        yield mock_redis

    app.dependency_overrides[get_db] = _fake_db
    app.dependency_overrides[get_redis] = _fake_redis

    transport = ASGITransport(app=app, raise_app_exceptions=False)

    async with AsyncClient(
        transport=transport,
        base_url="http://test",
    ) as client:
        resp = await client.get("/health/ready")

    assert resp.status_code == 503
    body = resp.json()
    assert body["status"] == "unavailable"
    assert body["db"] == "error"
    assert body["redis"] == "ok"


@pytest.mark.asyncio
async def test_readiness_503_when_redis_down() -> None:
    app = create_app()

    mock_db = AsyncMock()
    mock_redis = AsyncMock()
    mock_redis.ping.side_effect = Exception("redis down")

    async def _fake_db():
        yield mock_db

    async def _fake_redis():
        yield mock_redis

    app.dependency_overrides[get_db] = _fake_db
    app.dependency_overrides[get_redis] = _fake_redis

    transport = ASGITransport(app=app, raise_app_exceptions=False)

    async with AsyncClient(
        transport=transport,
        base_url="http://test",
    ) as client:
        resp = await client.get("/health/ready")

    assert resp.status_code == 503
    body = resp.json()
    assert body["status"] == "unavailable"
    assert body["db"] == "ok"
    assert body["redis"] == "error"