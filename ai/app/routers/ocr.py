import hashlib

from fastapi import APIRouter, Depends
from redis.asyncio import Redis

from app.clients import (
    get_gpu_client,
    get_s3_client,
)
from app.clients.gpu import GpuServerClient
from app.clients.s3 import S3Client
from app.core.errors import (
    BusinessException,
    CommonErrorCode,
)
from app.core.redis import get_redis
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.schemas.ocr import (
    BusinessCardOcrRequest,
    BusinessCardOcrResponse,
)

router = APIRouter(
    prefix="/api/v1/ocr",
    tags=["OCR"],
)

_BUSINESS_CARD_S3_PREFIX = "business-cards/"
_CACHE_TTL_SECONDS = 86400


def _cache_key(
    *,
    tenant_id: int,
    image_bytes: bytes,
) -> str:
    digest = hashlib.sha256(image_bytes).hexdigest()

    return f"ocr:bc:v3:{tenant_id}:{digest}"


@router.post("/business-card")
async def recognize_business_card(
    body: BusinessCardOcrRequest,
    tenant_id: int = Depends(get_tenant_id),
    s3: S3Client = Depends(get_s3_client),
    gpu: GpuServerClient = Depends(get_gpu_client),
    redis: Redis = Depends(get_redis),
) -> ApiResponse[BusinessCardOcrResponse]:

    # S3 prefix 검증
    if not body.s3_key.startswith(
        _BUSINESS_CARD_S3_PREFIX
    ):
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            (
                f"s3_key 는 "
                f"'{_BUSINESS_CARD_S3_PREFIX}' "
                f"프리픽스여야 합니다"
            ),
        )

    # S3 이미지 다운로드
    image_bytes = await s3.get_object(
        body.s3_key
    )

    # 캐시 키 생성
    cache_key = _cache_key(
        tenant_id=tenant_id,
        image_bytes=image_bytes,
    )

    # Redis 캐시 조회
    cached = await redis.get(cache_key)

    if cached is not None:
        return ApiResponse.ok(
            BusinessCardOcrResponse.model_validate_json(
                cached
            )
        )

    # GPU OCR 서버 호출
    response = await gpu.extract_business_card(
        image_bytes
    )

    # OCR 결과 캐시 저장
    await redis.set(
        cache_key,
        response.model_dump_json(),
        ex=_CACHE_TTL_SECONDS,
    )

    return ApiResponse.ok(response)