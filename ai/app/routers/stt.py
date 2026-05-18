import json
import logging
import uuid

from fastapi import APIRouter, BackgroundTasks, Depends, Request
from redis.asyncio import Redis

from app.clients import get_s3_client, get_whisper_client
from app.clients.gpu_stt import GpuSttClient
from app.clients.s3 import S3Client
from app.clients.whisper import WhisperClient
from app.core.errors import BusinessException, CommonErrorCode
from app.core.redis import get_redis
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.schemas.stt import (
    SttDiarizedResponse,
    SttJobResponse,
    SttJobStatus,
    SttRequest,
    SttSegment,
)

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/stt", tags=["STT"])

_JOB_TTL = 86400  # 24시간
_JOB_KEY_PREFIX = "stt:job:"

# 배치 전사 결과에서 걸러낼 Whisper 환각 패턴
# (실시간 스트림은 stt_stream.py 에서 별도로 처리)
_BATCH_HALLUCINATION_PATTERNS = (
    "자막은 설정에서",
    "시청해주셔서 감사합니다",
    "구독과 좋아요",
    "자막 제공",
    "자막을 사용",
    "다음 영상에서 만나요",
    "제작지원으로 제작",
    "영업 미팅 내용을 전사합니다",
    "MBC 뉴스",
    "KBS 뉴스",
    "SBS 뉴스",
    "翻訳",
    "字幕",
    "Subtitles by",
    "amara.org",
)


def _is_batch_hallucination(text: str) -> bool:
    return any(p in text for p in _BATCH_HALLUCINATION_PATTERNS)


def get_gpu_stt_client(request: Request) -> GpuSttClient | None:
    return getattr(request.app.state, "gpu_stt_client", None)


async def _process_transcription(
    job_id: str,
    body: SttRequest,
    redis: Redis,
    s3: S3Client,
    whisper: WhisperClient,
    gpu_stt: GpuSttClient | None,
) -> None:
    key = _JOB_KEY_PREFIX + job_id
    try:
        audio_bytes = await s3.get_object(body.s3_key)

        if body.diarize:
            if gpu_stt is None:
                raise BusinessException(
                    CommonErrorCode.EXTERNAL_API_FAILED,
                    "GPU STT 서버가 설정되지 않았습니다",
                )
            result: SttDiarizedResponse = await gpu_stt.transcribe_batch(
                audio_bytes, language=body.language
            )
            # GPU 서버 transcribe_batch 는 환각 필터를 거치지 않으므로 여기서 제거
            raw_count = len(result.segments)
            result.segments = [
                seg for seg in result.segments
                if seg.text.strip() and not _is_batch_hallucination(seg.text)
            ]
            if raw_count != len(result.segments):
                log.info(
                    "[STT batch] hallucination filtered: %d → %d segments",
                    raw_count, len(result.segments),
                )
            segments_data = [seg.model_dump() for seg in result.segments]
        else:
            transcript = await whisper.transcribe(audio_bytes, language=body.language)
            segments_data = [
                {"text": transcript, "speaker_id": "SPEAKER_00", "start_ms": 0, "end_ms": 0}
            ]

        await redis.set(
            key,
            json.dumps({"status": SttJobStatus.COMPLETED, "segments": segments_data}),
            ex=_JOB_TTL,
        )
        log.info("[STT job] completed job_id=%s segments=%d", job_id, len(segments_data))

    except Exception as e:
        log.exception("[STT job] failed job_id=%s", job_id)
        error_msg = e.detail if isinstance(e, BusinessException) else str(e)
        await redis.set(
            key,
            json.dumps({"status": SttJobStatus.FAILED, "error": error_msg}),
            ex=_JOB_TTL,
        )


@router.post("/transcribe", status_code=202)
async def transcribe(
    body: SttRequest,
    background_tasks: BackgroundTasks,
    tenant_id: int = Depends(get_tenant_id),
    s3: S3Client = Depends(get_s3_client),
    whisper: WhisperClient = Depends(get_whisper_client),
    gpu_stt: GpuSttClient | None = Depends(get_gpu_stt_client),
    redis: Redis = Depends(get_redis),
) -> ApiResponse[SttJobResponse]:
    job_id = str(uuid.uuid4())

    await redis.set(
        _JOB_KEY_PREFIX + job_id,
        json.dumps({"status": SttJobStatus.PROCESSING}),
        ex=_JOB_TTL,
    )

    background_tasks.add_task(
        _process_transcription, job_id, body, redis, s3, whisper, gpu_stt
    )

    log.info("[STT job] created job_id=%s s3_key=%s diarize=%s", job_id, body.s3_key, body.diarize)
    return ApiResponse.ok(SttJobResponse(job_id=job_id, status=SttJobStatus.PROCESSING))


@router.get("/jobs/{job_id}")
async def get_stt_job(
    job_id: str,
    tenant_id: int = Depends(get_tenant_id),
    redis: Redis = Depends(get_redis),
) -> ApiResponse[SttJobResponse]:
    raw = await redis.get(_JOB_KEY_PREFIX + job_id)
    if raw is None:
        raise BusinessException(CommonErrorCode.NOT_FOUND, f"STT 작업을 찾을 수 없습니다: {job_id}")

    data = json.loads(raw)
    status = SttJobStatus(data["status"])
    segments = None
    if status == SttJobStatus.COMPLETED:
        segments = [SttSegment(**seg) for seg in data.get("segments", [])]

    return ApiResponse.ok(
        SttJobResponse(
            job_id=job_id,
            status=status,
            segments=segments,
            error=data.get("error"),
        )
    )
