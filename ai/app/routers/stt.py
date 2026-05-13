from fastapi import APIRouter, Depends, Request

from app.clients import get_s3_client, get_whisper_client
from app.clients.gpu_stt import GpuSttClient
from app.clients.s3 import S3Client
from app.clients.whisper import WhisperClient
from app.core.errors import BusinessException, CommonErrorCode
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.schemas.stt import SttDiarizedResponse, SttRequest, SttResponse

router = APIRouter(prefix="/api/v1/stt", tags=["STT"])


def get_gpu_stt_client(request: Request) -> GpuSttClient | None:
    return getattr(request.app.state, "gpu_stt_client", None)


@router.post("/transcribe")
async def transcribe(
    body: SttRequest,
    tenant_id: int = Depends(get_tenant_id),
    s3: S3Client = Depends(get_s3_client),
    whisper: WhisperClient = Depends(get_whisper_client),
    gpu_stt: GpuSttClient | None = Depends(get_gpu_stt_client),
) -> ApiResponse[SttResponse | SttDiarizedResponse]:
    audio_bytes = await s3.get_object(body.s3_key)

    if body.diarize:
        if gpu_stt is None:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                "GPU STT 서버가 설정되지 않았습니다",
            )
        result = await gpu_stt.transcribe_batch(audio_bytes, language=body.language)
        return ApiResponse.ok(result)

    transcript = await whisper.transcribe(audio_bytes, language=body.language)
    return ApiResponse.ok(SttResponse(transcript=transcript))
