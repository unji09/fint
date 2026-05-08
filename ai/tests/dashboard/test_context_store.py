import pytest

from app.dashboard.context_store import ContextStore

MAX_ENTRIES = 5
TTL_SECONDS = 1800


@pytest.fixture
def fake_redis():
    """Redis async 인터페이스를 흉내내는 인메모리 스토어."""

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
    return ContextStore(fake_redis)


class TestContextStore:
    async def test_empty_context_returns_empty_list(self, store):
        result = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        assert result == []

    async def test_add_and_retrieve(self, store):
        await store.add_entry(
            tenant_id=42,
            dashboard_id=1,
            user_id=1,
            input_text="주간 매출 추이",
            search_type="STRUCTURED",
        )

        result = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        assert len(result) == 1
        assert result[0]["input_text"] == "주간 매출 추이"
        assert result[0]["search_type"] == "STRUCTURED"

    async def test_multiple_entries_ordered(self, store):
        for i in range(3):
            await store.add_entry(
                tenant_id=42,
                dashboard_id=1,
                user_id=1,
                input_text=f"질의 {i}",
                search_type="STRUCTURED",
            )

        result = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        assert len(result) == 3
        assert result[0]["input_text"] == "질의 0"
        assert result[2]["input_text"] == "질의 2"

    async def test_max_entries_evicts_oldest(self, store):
        for i in range(MAX_ENTRIES + 2):
            await store.add_entry(
                tenant_id=42,
                dashboard_id=1,
                user_id=1,
                input_text=f"질의 {i}",
                search_type="STRUCTURED",
            )

        result = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        assert len(result) == MAX_ENTRIES
        assert result[0]["input_text"] == "질의 2"
        assert result[-1]["input_text"] == "질의 6"

    async def test_different_users_isolated(self, store):
        await store.add_entry(
            tenant_id=42,
            dashboard_id=1,
            user_id=1,
            input_text="유저1 질의",
            search_type="STRUCTURED",
        )
        await store.add_entry(
            tenant_id=42,
            dashboard_id=1,
            user_id=2,
            input_text="유저2 질의",
            search_type="SEMANTIC",
        )

        user1 = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        user2 = await store.get_context(tenant_id=42, dashboard_id=1, user_id=2)

        assert len(user1) == 1
        assert user1[0]["input_text"] == "유저1 질의"
        assert len(user2) == 1
        assert user2[0]["input_text"] == "유저2 질의"

    async def test_different_dashboards_isolated(self, store):
        await store.add_entry(tenant_id=42, dashboard_id=1, user_id=1, input_text="대시보드1", search_type="STRUCTURED")
        await store.add_entry(tenant_id=42, dashboard_id=2, user_id=1, input_text="대시보드2", search_type="STRUCTURED")

        d1 = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        d2 = await store.get_context(tenant_id=42, dashboard_id=2, user_id=1)

        assert len(d1) == 1
        assert d1[0]["input_text"] == "대시보드1"
        assert len(d2) == 1
        assert d2[0]["input_text"] == "대시보드2"

    async def test_different_tenants_isolated(self, store):
        await store.add_entry(
            tenant_id=42,
            dashboard_id=1,
            user_id=1,
            input_text="테넌트42 질의",
            search_type="STRUCTURED",
        )
        await store.add_entry(
            tenant_id=99,
            dashboard_id=1,
            user_id=1,
            input_text="테넌트99 질의",
            search_type="STRUCTURED",
        )

        t42 = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        t99 = await store.get_context(tenant_id=99, dashboard_id=1, user_id=1)

        assert len(t42) == 1
        assert t42[0]["input_text"] == "테넌트42 질의"
        assert len(t99) == 1
        assert t99[0]["input_text"] == "테넌트99 질의"

    async def test_ttl_set_on_write(self, store, fake_redis):
        await store.add_entry(tenant_id=42, dashboard_id=1, user_id=1, input_text="테스트", search_type="STRUCTURED")

        key = "dashboard:context:42:1:1"
        assert key in fake_redis._ttls
        assert fake_redis._ttls[key] == TTL_SECONDS

    async def test_clear_context(self, store):
        await store.add_entry(tenant_id=42, dashboard_id=1, user_id=1, input_text="테스트", search_type="STRUCTURED")
        await store.clear_context(tenant_id=42, dashboard_id=1, user_id=1)

        result = await store.get_context(tenant_id=42, dashboard_id=1, user_id=1)
        assert result == []
