from unittest.mock import AsyncMock

import pytest
from httpx import ASGITransport, AsyncClient

from app.core.db import get_db
from app.core.redis import get_redis
from app.main import create_app


@pytest.mark.asyncio
async def test_health_returns_ok(client):
    response = await client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@pytest.mark.asyncio
async def test_readiness_returns_ready():
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
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.get("/health/ready")

    assert resp.status_code == 200
    assert resp.json() == {"status": "ready"}
    mock_db.execute.assert_called_once()
    mock_redis.ping.assert_called_once()


@pytest.mark.asyncio
async def test_readiness_fails_when_db_down():
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
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.get("/health/ready")

    assert resp.status_code == 500
