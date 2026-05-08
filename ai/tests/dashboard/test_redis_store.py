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
        assert data["result"] is None

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


class TestSetCompleted:
    async def test_completed_with_result(self, store, fake_redis):
        result = {"widget_type": "BAR_CHART", "title": "매출 추이"}
        await store.set_completed("trace-1", result)

        raw = await fake_redis.get("dashboard:query:trace-1")
        data = json.loads(raw)
        assert data["status"] == "COMPLETED"
        assert data["result"]["widget_type"] == "BAR_CHART"

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
