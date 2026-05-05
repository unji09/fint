import pytest
from httpx import ASGITransport, AsyncClient

from app.clients import get_s3_client, get_whisper_client
from app.core.errors import BusinessException, CommonErrorCode
from app.core.security import get_tenant_id
from app.main import create_app


class FakeS3:
    async def get_object(self, key: str) -> bytes:
        return b"fake-audio-data"


class FakeS3Failing:
    async def get_object(self, key: str) -> bytes:
        raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "S3 down")


class FakeWhisper:
    async def transcribe(self, audio_bytes: bytes, *, language: str = "ko") -> str:
        return "안녕하세요 테스트입니다"


@pytest.fixture
def app():
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.dependency_overrides[get_s3_client] = FakeS3
    application.dependency_overrides[get_whisper_client] = FakeWhisper
    return application


@pytest.fixture
async def client(app):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.mark.asyncio
async def test_transcribe_success(client):
    resp = await client.post(
        "/api/v1/stt/transcribe",
        json={"s3_key": "recordings/test.webm"},
    )

    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == 200
    assert body["data"]["transcript"] == "안녕하세요 테스트입니다"
    assert "code" not in body


@pytest.mark.asyncio
async def test_transcribe_with_language(client):
    resp = await client.post(
        "/api/v1/stt/transcribe",
        json={"s3_key": "recordings/test.webm", "language": "en"},
    )

    assert resp.status_code == 200


@pytest.mark.asyncio
async def test_transcribe_missing_s3_key(client):
    resp = await client.post("/api/v1/stt/transcribe", json={})

    assert resp.status_code == 400
    body = resp.json()
    assert body["code"] == "C001"


@pytest.mark.asyncio
async def test_transcribe_without_auth():
    app = create_app()
    app.dependency_overrides[get_s3_client] = FakeS3
    app.dependency_overrides[get_whisper_client] = FakeWhisper

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/stt/transcribe",
            json={"s3_key": "recordings/test.webm"},
        )

    assert resp.status_code == 401
    assert resp.json()["code"] == "C101"


@pytest.mark.asyncio
async def test_transcribe_s3_failure():
    app = create_app()
    app.dependency_overrides[get_tenant_id] = lambda: 1
    app.dependency_overrides[get_s3_client] = FakeS3Failing
    app.dependency_overrides[get_whisper_client] = FakeWhisper

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/stt/transcribe",
            json={"s3_key": "recordings/missing.webm"},
        )

    assert resp.status_code == 502
    assert resp.json()["code"] == "C502"
