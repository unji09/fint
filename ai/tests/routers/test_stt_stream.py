import base64
from unittest.mock import patch

import pytest
from jose import jwt
from starlette.testclient import TestClient

from app.clients.gpu_stt import GpuSttClient
from app.core.security import get_tenant_id
from app.main import create_app
from app.routers.stt import get_gpu_stt_client
from app.core.hallucination import clean_stream_text as _clean_text

# Spring JwtTokenProvider와 동일: base64url-encoded 시크릿 사용
_RAW_TEST_KEY = b"test-secret-key-12345678901234"
JWT_SECRET = base64.urlsafe_b64encode(_RAW_TEST_KEY).decode()
WS_URL = "/api/v1/stt/stream/activity_001"

# 1개 청크당 2KB — _CLUSTER_BUFFER_COUNT(2)개 모아서 보내야 GPU로 전달됨
_FAKE_AUDIO_CHUNK = b"\x00\x01\x02\x03" * 512  # 2KB


def _make_token(tenant_id: int = 1) -> str:
    return jwt.encode({"tenant_id": tenant_id}, _RAW_TEST_KEY, algorithm="HS256")


class FakeGpuStt:
    async def transcribe_chunk(
        self,
        audio_bytes: bytes,
        language: str = "ko",
        session_id: str = "",
        prev_text: str = "",
        beam_size: int = 5,
    ) -> dict:
        return {
            "text": "안녕하세요",
            "start_ms": 0,
            "end_ms": 1000,
            "speaker_id": "SPEAKER_00",
            "no_speech_prob": 0.05,
        }

    async def clear_session(self, session_id: str) -> None:
        pass


class FakeGpuSttEmpty:
    async def transcribe_chunk(
        self,
        audio_bytes: bytes,
        language: str = "ko",
        session_id: str = "",
        prev_text: str = "",
        beam_size: int = 5,
    ) -> dict:
        return {"text": "", "start_ms": 0, "end_ms": 0, "speaker_id": "SPEAKER_00", "no_speech_prob": 1.0}

    async def clear_session(self, session_id: str) -> None:
        pass


class FakeGpuSttFailing:
    async def transcribe_chunk(
        self,
        audio_bytes: bytes,
        language: str = "ko",
        session_id: str = "",
        prev_text: str = "",
        beam_size: int = 5,
    ) -> dict:
        from app.core.errors import BusinessException, CommonErrorCode
        raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "GPU 다운")

    async def clear_session(self, session_id: str) -> None:
        pass


def _make_app(fake_gpu: GpuSttClient | None = None) -> TestClient:
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.state.gpu_stt_client = fake_gpu
    application.dependency_overrides[get_gpu_stt_client] = lambda: fake_gpu
    return TestClient(application, raise_server_exceptions=False)


@patch("app.core.security.get_settings")
def test_stream_success(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuStt())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        for _ in range(2):
            ws.send_bytes(_FAKE_AUDIO_CHUNK)
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
        msg = ws.receive_json()  # 청크 크기 초과는 버퍼링 없이 즉시 응답

    assert msg["type"] == "error"
    assert "초과" in msg["message"]


@patch("app.core.security.get_settings")
def test_stream_empty_text_no_response(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuSttEmpty())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        ws.send_bytes(_FAKE_AUDIO_CHUNK)
        ws.send_bytes(_FAKE_AUDIO_CHUNK)


@patch("app.core.security.get_settings")
def test_stream_gpu_error_sends_error_message(mock_settings):
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuSttFailing())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        for _ in range(3):
            ws.send_bytes(_FAKE_AUDIO_CHUNK)
        msg = ws.receive_json()

    assert msg["type"] == "error"
    assert msg["message"] is not None


@patch("app.core.security.get_settings")
def test_stream_eos_flushes_remaining_buffer(mock_settings):
    """EOS 신호(0-byte) 수신 시 남은 버퍼를 플러시하고 stream_ended를 전송해야 한다."""
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuStt())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        # fast 버퍼 미달(1개)로 전송 — 일반적으로는 GPU 호출 없음
        ws.send_bytes(_FAKE_AUDIO_CHUNK)
        # EOS 신호 — 남은 버퍼를 강제 플러시해야 함
        ws.send_bytes(b"")
        # 전사 결과
        msg1 = ws.receive_json()
        # 완료 신호
        msg2 = ws.receive_json()

    assert msg1["type"] == "transcript"
    assert msg2["type"] == "stream_ended"


@patch("app.core.security.get_settings")
def test_stream_eos_empty_buffer_sends_stream_ended(mock_settings):
    """버퍼가 비어있을 때 EOS 신호를 받으면 즉시 stream_ended를 전송해야 한다."""
    mock_settings.return_value.JWT_SECRET = JWT_SECRET
    client = _make_app(fake_gpu=FakeGpuStt())  # type: ignore[arg-type]
    token = _make_token()

    with client.websocket_connect(f"{WS_URL}?token={token}") as ws:
        # 오디오 없이 바로 EOS
        ws.send_bytes(b"")
        msg = ws.receive_json()

    assert msg["type"] == "stream_ended"


# ── _clean_text 단위 테스트 ─────────────────────────────────────────────────────

def test_clean_text_passes_normal():
    assert _clean_text("안녕하세요 반갑습니다", 0.0) == "안녕하세요 반갑습니다"


def test_clean_text_blocks_high_no_speech():
    assert _clean_text("안녕하세요 반갑습니다", 0.9) == ""


def test_clean_text_strips_pure_hallucination():
    assert _clean_text("미팅 내용을 전사합니다.", 0.0) == ""


def test_clean_text_strips_hallucination_suffix():
    """실제 발화 + 환각 suffix → 실제 발화만 반환."""
    result = _clean_text("이걸로 1등을 맞출 것 같다. 미팅 내용을 전사합니다.", 0.0)
    assert "미팅 내용을 전사합니다" not in result
    assert "1등을 맞출 것 같다" in result


def test_clean_text_discards_short_prefix():
    """환각 제거 후 남은 텍스트가 너무 짧으면 버린다."""
    assert _clean_text("짠. 미팅 내용을 전사합니다.", 0.0) == ""


def test_clean_text_strips_variant_without_yeongop():
    """'영업' 없이 '미팅 내용을 전사합니다'만 있는 변형도 잡는다."""
    assert _clean_text("미팅 내용을 전사합니다", 0.0) == ""


def test_clean_text_strips_youtube_pattern():
    assert _clean_text("구독과 좋아요 눌러주세요", 0.0) == ""
