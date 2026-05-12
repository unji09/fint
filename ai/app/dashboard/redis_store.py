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

    async def set_failed(self, trace_id: str, error: str) -> None:
        await self._merge_update(trace_id, {
            "status": QueryStatus.FAILED.value,
            "error": error,
            "result": None,
        })

    async def get_result(self, trace_id: str) -> dict[str, Any] | None:
        raw = await self._redis.get(self._key(trace_id))
        if not raw:
            return None
        return json.loads(raw)
