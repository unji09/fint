import os
from pathlib import Path

import pytest

from app.clients.gpu_stt import GpuSttClient

RESOURCES = Path(__file__).parent.parent / "resources"


@pytest.fixture
def gpu_stt_client():
    """chunk 테스트용 — 짧은 응답 기대, 타임아웃 60초."""
    url = os.environ.get("GPU_SERVER_URL", "http://70.12.130.102:8002")
    return GpuSttClient(base_url=url, timeout=60.0)


@pytest.fixture
def gpu_stt_client_batch():
    """batch 테스트용 — 긴 파일 처리, 타임아웃 600초."""
    url = os.environ.get("GPU_SERVER_URL", "http://70.12.130.102:8002")
    return GpuSttClient(base_url=url, timeout=600.0)


@pytest.mark.real_gpu
async def test_real_stt_chunk(gpu_stt_client: GpuSttClient):
    """실제 GPU 서버 /stt/chunk 호출 테스트.
    tests/resources/test_chunk.m4a (2-3초 클립) 필요.
    없으면 test.m4a 사용.
    """
    audio_path = RESOURCES / "test_chunk.m4a"
    if not audio_path.exists():
        audio_path = RESOURCES / "test.m4a"
    if not audio_path.exists():
        pytest.skip(f"테스트 오디오 없음: {RESOURCES}")

    result = await gpu_stt_client.transcribe_chunk(audio_path.read_bytes(), language="ko")

    assert "text" in result
    assert "start_ms" in result
    assert "end_ms" in result
    assert "embedding" in result
    assert len(result["embedding"]) == 256
    print(f"\n[chunk] text={result['text']!r}, "
          f"start={result['start_ms']}ms, end={result['end_ms']}ms")


@pytest.mark.real_gpu
async def test_real_stt_batch(gpu_stt_client_batch: GpuSttClient):
    """실제 GPU 서버 /stt/batch 호출 테스트. tests/resources/test.m4a 필요."""
    audio_path = RESOURCES / "test.m4a"
    if not audio_path.exists():
        pytest.skip(f"테스트 오디오 없음: {audio_path}")

    result = await gpu_stt_client_batch.transcribe_batch(audio_path.read_bytes(), language="ko")

    assert len(result.segments) > 0
    for seg in result.segments:
        assert seg.speaker_id.startswith("SPEAKER_")
        assert seg.start_ms < seg.end_ms
        print(f"\n[{seg.speaker_id}] {seg.start_ms}ms-{seg.end_ms}ms  {seg.text!r}")
