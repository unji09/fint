import base64
from unittest.mock import patch

import pytest
from fastapi import APIRouter, Depends
from httpx import ASGITransport, AsyncClient
from jose import jwt

from app.core.security import get_tenant_id
from app.main import create_app

_test_router = APIRouter()


@_test_router.get("/test/tenant")
async def _get_tenant(tenant_id: int = Depends(get_tenant_id)):
    return {"tenant_id": tenant_id}


# Spring JwtTokenProvider와 동일: base64url-encoded 시크릿 사용
# _decode_secret()이 base64url decode하므로 테스트도 동일 방식을 적용해야 한다.
_RAW_TEST_KEY = b"test-secret-key-12345678901234"
JWT_SECRET = base64.urlsafe_b64encode(_RAW_TEST_KEY).decode()


@pytest.fixture
def app():
    application = create_app()
    application.include_router(_test_router)
    return application


@pytest.fixture
async def client(app):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


def _make_token(payload: dict) -> str:
    return jwt.encode(payload, _RAW_TEST_KEY, algorithm="HS256")


@pytest.mark.asyncio
@patch("app.core.security.get_settings")
async def test_jwt_extracts_tenant_id(mock_settings, client):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    token = _make_token({"tenant_id": 42})

    resp = await client.get("/test/tenant", headers={"Authorization": f"Bearer {token}"})

    assert resp.status_code == 200
    assert resp.json()["tenant_id"] == 42


@pytest.mark.asyncio
async def test_header_extracts_tenant_id(client):
    resp = await client.get("/test/tenant", headers={"X-Tenant-Id": "7"})

    assert resp.status_code == 200
    assert resp.json()["tenant_id"] == 7


@pytest.mark.asyncio
async def test_missing_auth_returns_401(client):
    resp = await client.get("/test/tenant")

    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == "C101"


@pytest.mark.asyncio
@patch("app.core.security.get_settings")
async def test_invalid_jwt_returns_401(mock_settings, client):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    resp = await client.get("/test/tenant", headers={"Authorization": "Bearer invalid.token.here"})

    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == "C101"
    assert body["message"] == "Invalid token"


@pytest.mark.asyncio
async def test_invalid_header_tenant_id_returns_401(client):
    resp = await client.get("/test/tenant", headers={"X-Tenant-Id": "not-a-number"})

    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == "C101"
    assert body["message"] == "Invalid X-Tenant-Id"


@pytest.mark.asyncio
@patch("app.core.security.get_settings")
async def test_jwt_takes_priority_over_header(mock_settings, client):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    token = _make_token({"tenant_id": 10})

    resp = await client.get(
        "/test/tenant",
        headers={"Authorization": f"Bearer {token}", "X-Tenant-Id": "99"},
    )

    assert resp.status_code == 200
    assert resp.json()["tenant_id"] == 10


@pytest.mark.asyncio
@patch("app.core.security.get_settings")
async def test_jwt_without_tenant_id_claim_returns_401(mock_settings, client):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    token = _make_token({"user_id": 1})

    resp = await client.get(
        "/test/tenant",
        headers={"Authorization": f"Bearer {token}", "X-Tenant-Id": "5"},
    )

    assert resp.status_code == 401
    assert resp.json()["message"] == "Missing tenant_id in token"


@pytest.mark.asyncio
@patch("app.core.security.get_settings")
async def test_tenant_id_zero_returns_401(mock_settings, client):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    token = _make_token({"tenant_id": 0})

    resp = await client.get("/test/tenant", headers={"Authorization": f"Bearer {token}"})

    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_header_tenant_id_zero_returns_401(client):
    resp = await client.get("/test/tenant", headers={"X-Tenant-Id": "0"})

    assert resp.status_code == 401


@pytest.mark.asyncio
@patch("app.core.security.get_settings")
async def test_empty_jwt_secret_returns_401(mock_settings, client):
    mock_settings.return_value.JWT_SECRET = ""
    token = _make_token({"tenant_id": 1})

    resp = await client.get("/test/tenant", headers={"Authorization": f"Bearer {token}"})

    assert resp.status_code == 401
