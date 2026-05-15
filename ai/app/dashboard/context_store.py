"""Redis TTL 기반 유저별 질의 맥락 관리."""

from __future__ import annotations

import json
from datetime import UTC, datetime

MAX_ENTRIES = 15
TTL_SECONDS = 1800


class ContextStore:
    def __init__(self, redis) -> None:
        self._redis = redis

    def _key(self, tenant_id: int, dashboard_id: int, user_id: int) -> str:
        return f"dashboard:context:{tenant_id}:{dashboard_id}:{user_id}"

    async def get_context(self, *, tenant_id: int, dashboard_id: int, user_id: int) -> list[dict]:
        raw = await self._redis.get(self._key(tenant_id, dashboard_id, user_id))
        if not raw:
            return []
        return json.loads(raw)

    async def add_entry(
        self,
        *,
        tenant_id: int,
        dashboard_id: int,
        user_id: int,
        input_text: str,
        search_type: str,
        suggested_title: str | None = None,
        source_query: str | None = None,
        row_count: int | None = None,
        columns: list[str] | None = None,
    ) -> None:
        key = self._key(tenant_id, dashboard_id, user_id)
        entries = await self.get_context(tenant_id=tenant_id, dashboard_id=dashboard_id, user_id=user_id)

        entries.append(
            {
                "input_text": input_text,
                "search_type": search_type,
                "suggested_title": suggested_title,
                "source_query": source_query,
                "row_count": row_count,
                "columns": columns,
                "timestamp": datetime.now(UTC).isoformat(),
            }
        )

        if len(entries) > MAX_ENTRIES:
            entries = entries[-MAX_ENTRIES:]

        await self._redis.set(key, json.dumps(entries, ensure_ascii=False), ex=TTL_SECONDS)

    async def clear_context(self, *, tenant_id: int, dashboard_id: int, user_id: int) -> None:
        await self._redis.delete(self._key(tenant_id, dashboard_id, user_id))
