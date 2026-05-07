"""대시보드 AI 질의 엔드포인트. trace_id 즉시 반환 + background task."""

from __future__ import annotations

import asyncio
import logging

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from redis.asyncio import Redis

from app.clients import get_llm_client
from app.clients.llm import LLMClient
from app.core.db import get_session_factory
from app.core.redis import get_redis
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.dashboard.context_store import ContextStore
from app.dashboard.query_engine import QueryEngine
from app.dashboard.redis_store import RedisStore
from app.schemas.dashboard import DashboardQueryRequest, QueryStatus

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/dashboard", tags=["Dashboard"])


class TraceIdResponse(BaseModel):
    trace_id: str


@router.post("/query")
async def query(
    body: DashboardQueryRequest,
    tenant_id: int = Depends(get_tenant_id),
    redis: Redis = Depends(get_redis),
    llm: LLMClient = Depends(get_llm_client),
) -> ApiResponse[TraceIdResponse]:
    body.tenant_id = tenant_id
    asyncio.create_task(run_query_task(request=body, redis=redis, llm=llm))
    return ApiResponse.ok(TraceIdResponse(trace_id=body.trace_id))


async def run_query_task(
    *,
    request: DashboardQueryRequest,
    redis,
    llm,
) -> None:
    redis_store = RedisStore(redis)
    context_store = ContextStore(redis)
    session_factory = get_session_factory()

    async def _on_status(status: QueryStatus) -> None:
        await redis_store.update_status(request.trace_id, status)

    try:
        async with session_factory() as db:
            engine = QueryEngine(llm=llm, db=db, context_store=context_store)
            result = await engine.run(request, on_status=_on_status)

        if result["status"] == "COMPLETED":
            await redis_store.set_completed(request.trace_id, result["result"])
        elif result["status"] == "REJECTED":
            error = result.get("rejection_reason", "요청을 처리할 수 없습니다.")
            await redis_store.set_failed(request.trace_id, error)
        else:
            await redis_store.set_failed(request.trace_id, result.get("error", "처리 중 오류가 발생했습니다."))
    except Exception as e:
        logger.exception("Dashboard query task failed: trace_id=%s", request.trace_id)
        await redis_store.set_failed(request.trace_id, str(e))
