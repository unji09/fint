import pytest
from httpx import ASGITransport, AsyncClient

from app.clients import get_s3_client, get_whisper_client
from app.core.errors import BusinessException, CommonErrorCode
from app.core.redis import get_redis
from app.core.security import get_tenant_id
from app.main import create_app
from app.routers.stt import get_gpu_stt_client


class FakeRedis:
    def __init__(self) -> None:
        self._store: dict[str, str] = {}

    async def set(self, key: str, value: str, ex: int | None = None) -> None:
        self._store[key] = value

    async def get(self, key: str) -> str | None:
        return self._store.get(key)


class FakeS3:
    async def get_object(self, key: str) -> bytes:
        return b"fake-audio-data"


class FakeS3Failing:
    async def get_object(self, key: str) -> bytes:
        raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "S3 down")


class FakeWhisper:
    async def transcribe(self, audio_bytes: bytes, *, language: str = "ko") -> str:
        return "안녕하세요 테스트입니다"


class FakeGpuStt:
    async def transcribe_batch(
        self, audio_bytes: bytes, language: str = "ko", num_speakers: int | None = None
    ):
        from app.schemas.stt import SttDiarizedResponse, SttSegment

        return SttDiarizedResponse(
            segments=[
                SttSegment(text="안녕하세요", speaker_id="SPEAKER_00", start_ms=0, end_ms=1000),
                SttSegment(text="반갑습니다", speaker_id="SPEAKER_01", start_ms=1200, end_ms=2500),
            ]
        )


class FakeGpuSttWithHallucination:

    async def transcribe_batch(
        self, audio_bytes: bytes, language: str = "ko", num_speakers: int | None = None
    ):
        from app.schemas.stt import SttDiarizedResponse, SttSegment

        return SttDiarizedResponse(
            segments=[
                SttSegment(text="안녕하세요", speaker_id="SPEAKER_00", start_ms=0, end_ms=1000),
                # 알려진 환각 패턴
                SttSegment(
                    text="자막은 설정에서 선택하실 수 있습니다",
                    speaker_id="SPEAKER_00",
                    start_ms=1100,
                    end_ms=1500,
                ),
                SttSegment(text="반갑습니다", speaker_id="SPEAKER_01", start_ms=1600, end_ms=2500),
                SttSegment(
                    text="시청해주셔서 감사합니다",
                    speaker_id="SPEAKER_00",
                    start_ms=2600,
                    end_ms=3000,
                ),
            ]
        )


# ── fixtures ────────────────────────────────────────────────────────────────


@pytest.fixture
def fake_redis() -> FakeRedis:
    return FakeRedis()


@pytest.fixture
def app(fake_redis: FakeRedis):
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.dependency_overrides[get_s3_client] = lambda: FakeS3()
    application.dependency_overrides[get_whisper_client] = lambda: FakeWhisper()
    application.dependency_overrides[get_redis] = lambda: fake_redis
    return application


@pytest.fixture
async def client(app):
    async with AsyncClient(transport=ASGITransport(app), base_url="http://test") as ac:
        yield ac


@pytest.fixture
def app_with_gpu(fake_redis: FakeRedis):
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.dependency_overrides[get_s3_client] = lambda: FakeS3()
    application.dependency_overrides[get_whisper_client] = lambda: FakeWhisper()
    application.dependency_overrides[get_gpu_stt_client] = lambda: FakeGpuStt()
    application.dependency_overrides[get_redis] = lambda: fake_redis
    return application


@pytest.fixture
async def client_with_gpu(app_with_gpu):
    async with AsyncClient(transport=ASGITransport(app_with_gpu), base_url="http://test") as ac:
        yield ac


# ── POST /transcribe ─────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_transcribe_submit_returns_202(client):
    resp = await client.post(
        "/api/v1/stt/transcribe",
        json={"s3_key": "recordings/test.webm"},
    )

    assert resp.status_code == 202
    data = resp.json()["data"]
    assert data["status"] == "PROCESSING"
    assert "job_id" in data


@pytest.mark.asyncio
async def test_transcribe_missing_s3_key(client):
    resp = await client.post("/api/v1/stt/transcribe", json={})

    assert resp.status_code == 400
    assert resp.json()["code"] == "C001"


@pytest.mark.asyncio
async def test_transcribe_without_auth():
    application = create_app()
    application.dependency_overrides[get_s3_client] = lambda: FakeS3()
    application.dependency_overrides[get_whisper_client] = lambda: FakeWhisper()
    application.dependency_overrides[get_redis] = lambda: FakeRedis()

    async with AsyncClient(transport=ASGITransport(application), base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/stt/transcribe",
            json={"s3_key": "recordings/test.webm"},
        )

    assert resp.status_code == 401
    assert resp.json()["code"] == "C101"


# ── GET /jobs/{job_id} ───────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_get_job_not_found(client):
    resp = await client.get("/api/v1/stt/jobs/nonexistent-job-id")

    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_transcribe_job_completed(client):
    """POST → background task → GET 순서로 COMPLETED + transcript 반환."""
    post = await client.post(
        "/api/v1/stt/transcribe",
        json={"s3_key": "recordings/test.webm"},
    )
    assert post.status_code == 202
    job_id = post.json()["data"]["job_id"]

    get = await client.get(f"/api/v1/stt/jobs/{job_id}")
    assert get.status_code == 200
    data = get.json()["data"]
    assert data["status"] == "COMPLETED"
    assert data["segments"][0]["text"] == "안녕하세요 테스트입니다"
    assert data["segments"][0]["speaker_id"] == "SPEAKER_00"


@pytest.mark.asyncio
async def test_transcribe_s3_failure_marks_job_failed():
    """S3 실패 시 job status가 FAILED로 기록된다."""
    fake_redis = FakeRedis()
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.dependency_overrides[get_s3_client] = lambda: FakeS3Failing()
    application.dependency_overrides[get_whisper_client] = lambda: FakeWhisper()
    application.dependency_overrides[get_redis] = lambda: fake_redis

    async with AsyncClient(transport=ASGITransport(application), base_url="http://test") as ac:
        post = await ac.post(
            "/api/v1/stt/transcribe",
            json={"s3_key": "recordings/missing.webm"},
        )
        assert post.status_code == 202
        job_id = post.json()["data"]["job_id"]

        get = await ac.get(f"/api/v1/stt/jobs/{job_id}")
        assert get.status_code == 200
        assert get.json()["data"]["status"] == "FAILED"


# ── diarize=True ─────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_transcribe_diarize_success(client_with_gpu):
    post = await client_with_gpu.post(
        "/api/v1/stt/transcribe",
        json={"s3_key": "recordings/test.m4a", "diarize": True},
    )
    assert post.status_code == 202
    job_id = post.json()["data"]["job_id"]

    get = await client_with_gpu.get(f"/api/v1/stt/jobs/{job_id}")
    assert get.status_code == 200
    data = get.json()["data"]
    assert data["status"] == "COMPLETED"
    assert len(data["segments"]) == 2
    assert data["segments"][0]["speaker_id"] == "SPEAKER_00"
    assert data["segments"][1]["speaker_id"] == "SPEAKER_01"
    assert data["segments"][0]["text"] == "안녕하세요"


@pytest.mark.asyncio
async def test_transcribe_diarize_hallucination_filtered():
    """배치 전사 결과에서 환각 세그먼트가 제거된다."""
    fake_redis = FakeRedis()
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.dependency_overrides[get_s3_client] = lambda: FakeS3()
    application.dependency_overrides[get_whisper_client] = lambda: FakeWhisper()
    application.dependency_overrides[get_gpu_stt_client] = lambda: FakeGpuSttWithHallucination()
    application.dependency_overrides[get_redis] = lambda: fake_redis

    async with AsyncClient(transport=ASGITransport(application), base_url="http://test") as ac:
        post = await ac.post(
            "/api/v1/stt/transcribe",
            json={"s3_key": "recordings/test.m4a", "diarize": True},
        )
        assert post.status_code == 202
        job_id = post.json()["data"]["job_id"]

        get = await ac.get(f"/api/v1/stt/jobs/{job_id}")
        assert get.status_code == 200
        data = get.json()["data"]
        assert data["status"] == "COMPLETED"
        texts = [s["text"] for s in data["segments"]]
        # 환각 패턴이 포함된 2개 세그먼트는 제거되고 실제 발화 2개만 남아야 함
        assert len(texts) == 2
        assert "자막은 설정에서 선택하실 수 있습니다" not in texts
        assert "시청해주셔서 감사합니다" not in texts
        assert "안녕하세요" in texts
        assert "반갑습니다" in texts


@pytest.mark.asyncio
async def test_transcribe_diarize_no_gpu():
    """GPU STT 미설정 시 job status가 FAILED로 기록된다."""
    fake_redis = FakeRedis()
    application = create_app()
    application.dependency_overrides[get_tenant_id] = lambda: 1
    application.dependency_overrides[get_s3_client] = lambda: FakeS3()
    application.dependency_overrides[get_whisper_client] = lambda: FakeWhisper()
    application.dependency_overrides[get_gpu_stt_client] = lambda: None
    application.dependency_overrides[get_redis] = lambda: fake_redis

    async with AsyncClient(transport=ASGITransport(application), base_url="http://test") as ac:
        post = await ac.post(
            "/api/v1/stt/transcribe",
            json={"s3_key": "recordings/test.m4a", "diarize": True},
        )
        assert post.status_code == 202
        job_id = post.json()["data"]["job_id"]

        get = await ac.get(f"/api/v1/stt/jobs/{job_id}")
        assert get.status_code == 200
        assert get.json()["data"]["status"] == "FAILED"
