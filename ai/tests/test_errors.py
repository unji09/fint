import pytest
from fastapi import APIRouter
from httpx import ASGITransport, AsyncClient
from pydantic import BaseModel

from app.core.errors import BusinessException, CommonErrorCode
from app.core.response import ApiResponse


class _DummyBody(BaseModel):
    name: str
    age: int


_test_router = APIRouter()


@_test_router.post("/test/business-error")
async def _raise_business():
    raise BusinessException(CommonErrorCode.NOT_FOUND)


@_test_router.post("/test/business-error-custom")
async def _raise_business_custom():
    raise BusinessException(CommonErrorCode.NOT_FOUND, "User 42 not found")


@_test_router.post("/test/validation")
async def _validate(body: _DummyBody):
    return body


@_test_router.post("/test/unhandled")
async def _unhandled():
    raise RuntimeError("something broke")


@_test_router.get("/test/http-unmapped")
async def _http_unmapped():
    from starlette.exceptions import HTTPException as StarletteHTTPException

    raise StarletteHTTPException(status_code=429, detail="Too many requests")


@pytest.fixture
def app():
    from app.main import create_app

    application = create_app()
    application.include_router(_test_router)
    return application


@pytest.fixture
async def client(app):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture
async def client_no_raise(app):
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.mark.asyncio
async def test_business_exception_returns_error_response(client):
    resp = await client.post("/test/business-error")

    assert resp.status_code == 404
    body = resp.json()
    assert body["status"] == 404
    assert body["code"] == "C301"
    assert body["message"] == "Resource not found"
    assert body["data"] is None


@pytest.mark.asyncio
async def test_business_exception_custom_message(client):
    resp = await client.post("/test/business-error-custom")

    assert resp.status_code == 404
    body = resp.json()
    assert body["code"] == "C301"
    assert body["message"] == "User 42 not found"


@pytest.mark.asyncio
async def test_validation_error_returns_field_details(client):
    resp = await client.post("/test/validation", json={"name": 123})

    assert resp.status_code == 400
    body = resp.json()
    assert body["code"] == "C001"
    assert isinstance(body["data"], dict)


@pytest.mark.asyncio
async def test_unhandled_exception_returns_500(client_no_raise):
    resp = await client_no_raise.post("/test/unhandled")

    assert resp.status_code == 500
    body = resp.json()
    assert body["code"] == "C500"
    assert body["data"] is None


@pytest.mark.asyncio
async def test_http_exception_unmapped_code_excludes_code_field(client):
    resp = await client.get("/test/http-unmapped")

    assert resp.status_code == 429
    body = resp.json()
    assert "code" not in body
    assert body["message"] == "Too many requests"


@pytest.mark.asyncio
async def test_success_response_excludes_code():
    resp = ApiResponse.ok({"key": "value"})
    dumped = resp.model_dump()
    assert "code" not in dumped
    assert dumped["status"] == 200
    assert dumped["message"] == "success"
    assert dumped["data"] == {"key": "value"}
