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

    async def update_status(self, trace_id: str, status: QueryStatus) -> None:
        payload = {"status": status.value, "result": None}
        await self._redis.set(
            self._key(trace_id),
            json.dumps(payload, ensure_ascii=False),
            ex=RESULT_TTL,
        )

    async def set_completed(self, trace_id: str, result: dict[str, Any]) -> None:
        payload = {"status": QueryStatus.COMPLETED.value, "result": result}
        await self._redis.set(
            self._key(trace_id),
            json.dumps(payload, ensure_ascii=False),
            ex=RESULT_TTL,
        )

    async def set_failed(self, trace_id: str, error: str) -> None:
        payload = {"status": QueryStatus.FAILED.value, "error": error, "result": None}
        await self._redis.set(
            self._key(trace_id),
            json.dumps(payload, ensure_ascii=False),
            ex=RESULT_TTL,
        )

    async def get_result(self, trace_id: str) -> dict[str, Any] | None:
        raw = await self._redis.get(self._key(trace_id))
        if not raw:
            return None
        return json.loads(raw)
