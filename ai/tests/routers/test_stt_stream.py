import base64
from pathlib import Path
from unittest.mock import patch

import pytest
from jose import jwt
from starlette.testclient import TestClient

from app.clients.gpu_stt import GpuSttClient
from app.core.security import get_tenant_id
from app.main import create_app
from app.routers.stt import get_gpu_stt_client
from app.core.speaker_session import SpeakerSessionManager

# Spring JwtTokenProvider와 동일: base64url-encoded 시크릿 사용
_RAW_TEST_KEY = b"test-secret-key-12345678901234"
JWT_SECRET = base64.urlsafe_b64encode(_RAW_TEST_KEY).decode()
WS_URL = "/api/v1/stt/stream/activity_001"

# 실제 오디오 청크 로드 — WS 스트리밍은 청크 단위로 전송하므로 test_chunk.m4a 사용
# (test.m4a는 57MB 전체 녹음본으로 1MB 청크 제한 초과)
_TEST_AUDIO_CHUNK = (Path(__file__).parent.parent / "resources" / "test_chunk.m4a").read_bytes()


def _make_token(tenant_id: int = 1) -> str:
    return jwt.encode({"tenant_id": tenant_id}, _RAW_TEST_KEY, algorithm="HS256")


class FakeGpuStt:
    async def transcribe_chunk(self, audio_bytes: bytes, language: str = "ko") -> dict:
        return {
            "text": "안녕하세요",
            "start_ms": 0,
            "end_ms": 1000,
            "embedding": [1.0] + [0.0] * 255,
        }


class FakeGpuSttEmpty:
    async def transcribe_chunk(self, audio_bytes: bytes, language: str = "ko") -> dict:
        return {"text": "", "start_ms": 0, "end_ms": 0, "embedding": [0.0] * 256}


class FakeGpuSttFailing:
    async def transcribe_chunk(self, audio_bytes: bytes, language: str = "ko") -> dict:
        from app.core.errors import BusinessException, CommonErrorCode
        raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "GPU 다운")


def _make_app(fake_gpu: GpuSttClient | None = None) -> TestClient:
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.state.gpu_stt_client = fake_gpu
    application.state.speaker_session_manager = SpeakerSessionManager()
    application.dependency_overrides[get_gpu_stt_client] = lambda: fake_gpu
    return TestClient(application, raise_server_exceptions=False)


@patch("app.core.security.get_settings")
def test_stream_success(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuStt())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        ws.send_bytes(_TEST_AUDIO_CHUNK)
        msg = ws.receive_json()

    assert msg["type"] == "transcript"
    assert msg["segment"]["text"] == "안녕하세요"
    assert msg["segment"]["speaker_id"] == "SPEAKER_00"
    assert msg["segment"]["start_ms"] == 0
    assert msg["segment"]["end_ms"] == 1000


@patch("app.core.security.get_settings")
def test_stream_invalid_token(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuStt())  # type: ignore[arg-type]

    with pytest.raises(Exception):
        with client.websocket_connect(f"{WS_URL}?token=invalid.token.here") as ws:
            ws.receive_json()


@patch("app.core.security.get_settings")
def test_stream_no_gpu_configured(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=None)
    token = _make_token()

    with pytest.raises(Exception):
        with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
            ws.receive_json()


@patch("app.core.security.get_settings")
def test_stream_oversized_chunk(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuStt())  # type: ignore[arg-type]
    token = _make_token()

    oversized = b"x" * (1 * 1024 * 1024 + 1)

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        ws.send_bytes(oversized)
        msg = ws.receive_json()

    assert msg["type"] == "error"
    assert "초과" in msg["message"]


@patch("app.core.security.get_settings")
def test_stream_empty_text_no_response(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuSttEmpty())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        ws.send_bytes(_TEST_AUDIO_CHUNK)
        ws.send_bytes(_TEST_AUDIO_CHUNK)


@patch("app.core.security.get_settings")
def test_stream_gpu_error_sends_error_message(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuSttFailing())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        ws.send_bytes(_TEST_AUDIO_CHUNK)
        msg = ws.receive_json()

    assert msg["type"] == "error"
    assert msg["message"] is not None


@patch("app.core.security.get_settings")
def test_stream_session_isolated_per_tenant(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET

    manager = SpeakerSessionManager()
    application = create_app()
    application.state.gpu_stt_client = FakeGpuStt()
    application.state.speaker_session_manager = manager
    application.dependency_overrides[get_gpu_stt_client] = lambda: FakeGpuStt()
    client = TestClient(application, raise_server_exceptions=False)

    token_t1 = jwt.encode({"tenant_id": 1}, _RAW_TEST_KEY, algorithm="HS256")
    token_t2 = jwt.encode({"tenant_id": 2}, _RAW_TEST_KEY, algorithm="HS256")

    with client.websocket_connect(f"{WS_URL}?token={token_t1}") as ws:
        ws.send_bytes(_TEST_AUDIO_CHUNK)
        ws.receive_json()

    with client.websocket_connect(f"{WS_URL}?token={token_t2}") as ws:
        ws.send_bytes(_TEST_AUDIO_CHUNK)
        ws.receive_json()

    assert "1:activity_001" not in manager._sessions
    assert "2:activity_001" not in manager._sessions
