"""trace_id별 대시보드 질의 상태/결과 관리. Spring polling 대상."""

from __future__ import annotations

import json
from typing import Any

from app.schemas.dashboard import QueryStatus

RESULT_TTL = 600


class RedisStore:
    def __init__(self, redis) -> None:
        self._redis = redis

    def _key(self, trace_id: str) -> str:
        return f"dashboard:query:{trace_id}"

    async def _merge_update(self, trace_id: str, updates: dict[str, Any]) -> None:
        key = self._key(trace_id)
        raw = await self._redis.get(key)
        payload = json.loads(raw) if raw else {}
        payload.update(updates)
        await self._redis.set(
            key,
            json.dumps(payload, ensure_ascii=False, default=str),
            ex=RESULT_TTL,
        )

    async def update_status(self, trace_id: str, status: QueryStatus) -> None:
        await self._merge_update(trace_id, {"status": status.value})

    async def set_completed(self, trace_id: str, result: dict[str, Any]) -> None:
        await self._merge_update(trace_id, {
            "status": QueryStatus.COMPLETED.value,
            "result": result,
        })

    async def set_failed(self, trace_id: str, error: str, *, error_code: str | None = None) -> None:
        await self._merge_update(trace_id, {
            "status": QueryStatus.FAILED.value,
            "error": error,
            "error_code": error_code,
            "result": None,
        })

    async def get_result(self, trace_id: str) -> dict[str, Any] | None:
        raw = await self._redis.get(self._key(trace_id))
        if not raw:
            return None
        return json.loads(raw)

    # --- Rate limiting ---

    _MAX_CONCURRENT = 3
    _RATE_TTL = 60

    def _rate_key(self, tenant_id: int, user_id: int) -> str:
        return f"dashboard:rate:{tenant_id}:{user_id}"

    async def acquire_rate_slot(self, *, tenant_id: int, user_id: int) -> bool:
        key = self._rate_key(tenant_id, user_id)
        count = await self._redis.incr(key)
        if count == 1:
            await self._redis.expire(key, self._RATE_TTL)
        if count > self._MAX_CONCURRENT:
            await self._redis.decr(key)
            return False
        return True

    async def release_rate_slot(self, *, tenant_id: int, user_id: int) -> None:
        key = self._rate_key(tenant_id, user_id)
        val = await self._redis.decr(key)
        if val <= 0:
            await self._redis.delete(key)
