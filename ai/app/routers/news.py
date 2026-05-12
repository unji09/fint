"""POST /ai/signals/collect — 외부 데이터(뉴스/공시) 수집 엔드포인트."""
import logging

from fastapi import APIRouter, Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession

from app.clients import get_embedder_client, get_naver_client
from app.clients.embedder import OnnxEmbedderClient
from app.clients.naver import NaverNewsClient
from app.core.db import get_db
from app.core.errors import BusinessException, CommonErrorCode
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.news.collector import collect_news
from app.schemas.news import SignalsCollectRequest, SignalsCollectResponse

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["signals"])


@router.post("/signals/collect")
async def collect_signals_endpoint(
    body: SignalsCollectRequest,
    tenant_id: int = Depends(get_tenant_id),
    x_tenant_id: str | None = Header(None, include_in_schema=True),
    db: AsyncSession = Depends(get_db),
    naver_client: NaverNewsClient = Depends(get_naver_client),
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
        embedder=embedder,
        include_embeddings=body.include_embeddings,
    )

    logger.info(
        "signals collect completed: tenant_id=%d, source=%s, news_new=%d",
        tenant_id, body.source, len(result.news.new_articles),
    )
    return ApiResponse.ok(result)