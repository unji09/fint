import json

import pytest

from app.dashboard.redis_store import RESULT_TTL, RedisStore
from app.schemas.dashboard import QueryStatus


@pytest.fixture
def fake_redis():
    class FakeRedis:
        def __init__(self):
            self._store: dict[str, str] = {}
            self._ttls: dict[str, int] = {}

        async def get(self, key: str) -> str | None:
            return self._store.get(key)

        async def set(self, key: str, value: str, *, ex: int | None = None) -> None:
            self._store[key] = value
            if ex:
                self._ttls[key] = ex

        async def delete(self, key: str) -> None:
            self._store.pop(key, None)
            self._ttls.pop(key, None)

        async def incr(self, key: str) -> int:
            raw = self._store.get(key)
            val = int(raw) if raw else 0
            val += 1
            self._store[key] = str(val)
            return val

        async def decr(self, key: str) -> int:
            raw = self._store.get(key)
            val = int(raw) if raw else 0
            val -= 1
            self._store[key] = str(val)
            return val

        async def expire(self, key: str, seconds: int) -> None:
            self._ttls[key] = seconds

    return FakeRedis()


@pytest.fixture
def store(fake_redis):
    return RedisStore(fake_redis)


class TestUpdateStatus:
    async def test_sets_status_in_redis(self, store, fake_redis):
        await store.update_status("trace-1", QueryStatus.INTENT_PARSING)

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data["status"] == "INTENT_PARSING"

    async def test_status_has_ttl(self, store, fake_redis):
        await store.update_status("trace-1", QueryStatus.DATA_QUERYING)

        assert fake_redis._ttls["dashboard:query:trace-1"] == RESULT_TTL

    async def test_status_progression(self, store, fake_redis):
        for status in [
            QueryStatus.INTENT_PARSING,
            QueryStatus.DATA_QUERYING,
            QueryStatus.COMPONENT_BUILDING,
            QueryStatus.STYLING,
        ]:
            await store.update_status("trace-1", status)

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data["status"] == "STYLING"

    async def test_preserves_existing_fields(self, store, fake_redis):
        """Spring이 먼저 저장한 dashboardId 등이 update_status로 덮어쓰이지 않아야 한다."""
        initial = {"status": "PENDING", "dashboardId": 42, "inputText": "매출 보여줘"}
        await fake_redis.set(
            "dashboard:query:trace-1",
            json.dumps(initial),
            ex=RESULT_TTL,
        )

        await store.update_status("trace-1", QueryStatus.INTENT_PARSING)

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data["status"] == "INTENT_PARSING"
        assert data["dashboardId"] == 42
        assert data["inputText"] == "매출 보여줘"


class TestSetCompleted:
    async def test_completed_with_result(self, store, fake_redis):
        result = {"widget_type": "CHART", "title": "매출 추이"}
        await store.set_completed("trace-1", result)

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data["status"] == "COMPLETED"
        assert data["result"]["widget_type"] == "CHART"

    async def test_completed_has_ttl(self, store, fake_redis):
        await store.set_completed("trace-1", {"title": "test"})

        assert fake_redis._ttls["dashboard:query:trace-1"] == RESULT_TTL


class TestSetFailed:
    async def test_failed_with_error(self, store, fake_redis):
        await store.set_failed("trace-1", "허용되지 않은 테이블")

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data["status"] == "FAILED"
        assert data["error"] == "허용되지 않은 테이블"

    async def test_failed_has_ttl(self, store, fake_redis):
        await store.set_failed("trace-1", "에러")

        assert fake_redis._ttls["dashboard:query:trace-1"] == RESULT_TTL

    async def test_failed_with_error_code(self, store, fake_redis):
        await store.set_failed("trace-1", "DB 오류", error_code="DB_ERROR")

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data["error_code"] == "DB_ERROR"
        assert data["error"] == "DB 오류"

    async def test_failed_without_error_code_defaults_none(self, store, fake_redis):
        await store.set_failed("trace-1", "에러")

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data.get("error_code") is None


class TestGetResult:
    async def test_get_existing_result(self, store):
        await store.set_completed("trace-1", {"title": "test"})

        result = await store.get_result("trace-1")
        assert result["status"] == "COMPLETED"

    async def test_get_nonexistent_returns_none(self, store):
        result = await store.get_result("nonexistent")
        assert result is None


class TestDifferentTraceIds:
    async def test_traces_isolated(self, store):
        await store.set_completed("trace-1", {"title": "결과1"})
        await store.set_failed("trace-2", "에러")

        r1 = await store.get_result("trace-1")
        r2 = await store.get_result("trace-2")
        assert r1["status"] == "COMPLETED"
        assert r2["status"] == "FAILED"


class TestRateLimit:
    async def test_acquire_within_limit(self, store):
        ok = await store.acquire_rate_slot(tenant_id=1, user_id=7)
        assert ok is True

    async def test_acquire_exceeds_limit(self, store):
        for _ in range(3):
            await store.acquire_rate_slot(tenant_id=1, user_id=7)
        ok = await store.acquire_rate_slot(tenant_id=1, user_id=7)
        assert ok is False

    async def test_release_allows_new_slot(self, store):
        for _ in range(3):
            await store.acquire_rate_slot(tenant_id=1, user_id=7)
        await store.release_rate_slot(tenant_id=1, user_id=7)
        ok = await store.acquire_rate_slot(tenant_id=1, user_id=7)
        assert ok is True

    async def test_different_users_isolated(self, store):
        for _ in range(3):
            await store.acquire_rate_slot(tenant_id=1, user_id=7)
        ok = await store.acquire_rate_slot(tenant_id=1, user_id=8)
        assert ok is True
