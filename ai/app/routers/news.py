"""POST /api/v1/signals/collect — 외부 데이터(뉴스/공시) 수집 엔드포인트."""
import logging

from fastapi import APIRouter, Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession

from app.clients import get_dart_client, get_embedder_client, get_naver_client
from app.clients.dart import DartClient
from app.clients.embedder import OnnxEmbedderClient
from app.clients.naver import NaverNewsClient
from app.core.db import get_db
from app.core.errors import BusinessException, CommonErrorCode
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.news.collector import collect_news
from app.schemas.news import SignalsCollectRequest, SignalsCollectResponse

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/signals", tags=["signals"])


@router.post("/collect")
async def collect_signals_endpoint(
    body: SignalsCollectRequest,
    tenant_id: int = Depends(get_tenant_id),
    x_tenant_id: str | None = Header(None, include_in_schema=True),
    db: AsyncSession = Depends(get_db),
    naver_client: NaverNewsClient = Depends(get_naver_client),
    dart_client: DartClient | None = Depends(get_dart_client),
    embedder: OnnxEmbedderClient | None = Depends(get_embedder_client),
) -> ApiResponse[SignalsCollectResponse]:
    if body.source in ("naver", "all") and embedder is None:
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            "임베딩 모델이 로드되지 않았습니다. EMBEDDING_MODEL_PATH를 확인하세요.",
        )

    result = await collect_news(
        source=body.source,
        tenant_id=tenant_id,
        db=db,
        naver_client=naver_client,
        dart_client=dart_client,
        embedder=embedder,
        include_embeddings=body.include_embeddings,
        account_ids=body.account_ids,
    )

    logger.info(
        "signals collect completed: tenant_id=%d, source=%s, news=%d, dart=%d",
        tenant_id, body.source,
        len(result.news.new_articles), len(result.dart.new_disclosures),
    )
    return ApiResponse.ok(result)
