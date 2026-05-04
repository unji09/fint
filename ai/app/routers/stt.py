from fastapi import APIRouter, Depends

from app.clients import get_s3_client, get_whisper_client
from app.clients.s3 import S3Client
from app.clients.whisper import WhisperClient
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.schemas.stt import SttRequest, SttResponse

router = APIRouter(prefix="/api/v1/stt", tags=["STT"])


@router.post("/transcribe")
async def transcribe(
    body: SttRequest,
    tenant_id: int = Depends(get_tenant_id),
    s3: S3Client = Depends(get_s3_client),
    whisper: WhisperClient = Depends(get_whisper_client),
) -> ApiResponse[SttResponse]:
    audio_bytes = await s3.get_object(body.s3_key)
    transcript = await whisper.transcribe(audio_bytes, language=body.language)
    return ApiResponse.ok(SttResponse(transcript=transcript))
