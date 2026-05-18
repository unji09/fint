"""POST /api/v1/company/enrich 엔드포인트 테스트."""
from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.clients import get_dart_client
from app.clients.dart import CompanyInfo, CorpInfo, DartClient
from app.core.errors import register_exception_handlers
from app.core.security import get_tenant_id
from app.routers.company import router

TENANT_ID = 42

SAMPLE_CORP = CorpInfo("00126186", "삼성에스디에스", "018260")
SAMPLE_COMPANY = CompanyInfo(
    corp_code="00126186",
    corp_name="삼성에스디에스(주)",
    ceo_nm="이준희",
    bizr_no="1108128774",
    induty_code="62021",
    adres="서울특별시 송파구",
    phn_no="02-6155-3114",
    est_dt="19850501",
)


def _make_mock_dart(fuzzy_result: list[CorpInfo], company_info: CompanyInfo | None = SAMPLE_COMPANY):
    dart = MagicMock(spec=DartClient)
    dart.ensure_corp_codes = AsyncMock()
    dart.find_corp_fuzzy = MagicMock(return_value=fuzzy_result)
    dart.get_company_info = AsyncMock(return_value=company_info)
    return dart


@pytest.fixture
def mock_dart():
    return _make_mock_dart([SAMPLE_CORP])


@pytest.fixture
def app_with_dart(mock_dart):
    app = FastAPI()
    register_exception_handlers(app)
    app.include_router(router)
    app.dependency_overrides = {
        get_tenant_id: lambda: TENANT_ID,
        get_dart_client: lambda: mock_dart,
    }
    return app


@pytest.fixture
async def ac(app_with_dart) -> AsyncClient:
    async with AsyncClient(
        transport=ASGITransport(app=app_with_dart),
        base_url="http://test",
    ) as client:
        yield client


class TestEnrichSuccess:
    async def test_single_match_returns_matched_true(self, ac, mock_dart):
        resp = await ac.post("/api/v1/company/enrich", json={"company_name": "삼성SDS"})
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["matched"] is True
        assert data["company"]["biz_no"] == "1108128774"
        assert data["company"]["corp_name"] == "삼성에스디에스(주)"
        assert data["candidates"] == []

    async def test_single_match_company_api_fails_returns_partial(self, app_with_dart):
        dart = _make_mock_dart([SAMPLE_CORP], company_info=None)
        app_with_dart.dependency_overrides[get_dart_client] = lambda: dart

        async with AsyncClient(
            transport=ASGITransport(app=app_with_dart), base_url="http://test",
        ) as ac:
            resp = await ac.post("/api/v1/company/enrich", json={"company_name": "삼성SDS"})

        data = resp.json()["data"]
        assert data["matched"] is True
        assert data["company"]["corp_code"] == "00126186"
        assert data["company"]["biz_no"] is None


class TestEnrichMultipleCandidates:
    async def test_multiple_matches_returns_candidates(self, app_with_dart):
        corps = [
            CorpInfo("00164742", "현대자동차", "005380"),
            CorpInfo("00164779", "현대건설", "000720"),
        ]
        infos = [
            CompanyInfo("00164742", "현대자동차", "정의선", "1018116390", "45111", "서울", "02-1234", "19670601"),
            CompanyInfo("00164779", "현대건설", "윤영준", "1018122092", "41220", "서울", "02-5678", "19500110"),
        ]
        dart = MagicMock(spec=DartClient)
        dart.ensure_corp_codes = AsyncMock()
        dart.find_corp_fuzzy = MagicMock(return_value=corps)
        dart.get_company_info = AsyncMock(side_effect=infos)
        app_with_dart.dependency_overrides[get_dart_client] = lambda: dart

        async with AsyncClient(
            transport=ASGITransport(app=app_with_dart), base_url="http://test",
        ) as ac:
            resp = await ac.post("/api/v1/company/enrich", json={"company_name": "현대자동"})

        data = resp.json()["data"]
        assert data["matched"] is False
        assert data["company"] is None
        assert len(data["candidates"]) == 2
        names = {c["corp_name"] for c in data["candidates"]}
        assert "현대자동차" in names
        assert "현대건설" in names

    async def test_multiple_matches_calls_api_concurrently(self, app_with_dart):
        """다수 후보 시 get_company_info가 병렬(gather)로 호출되는지 확인."""
        corps = [
            CorpInfo("00000001", "A사", "111111"),
            CorpInfo("00000002", "B사", "222222"),
            CorpInfo("00000003", "C사", "333333"),
        ]
        infos = [
            CompanyInfo("00000001", "A사", "대표A", "1111111111", "10000", "서울", "02-1111", "20000101"),
            CompanyInfo("00000002", "B사", "대표B", "2222222222", "20000", "부산", "051-2222", "20100101"),
            CompanyInfo("00000003", "C사", "대표C", "3333333333", "30000", "대전", "042-3333", "20200101"),
        ]
        dart = MagicMock(spec=DartClient)
        dart.ensure_corp_codes = AsyncMock()
        dart.find_corp_fuzzy = MagicMock(return_value=corps)
        dart.get_company_info = AsyncMock(side_effect=infos)
        app_with_dart.dependency_overrides[get_dart_client] = lambda: dart

        async with AsyncClient(
            transport=ASGITransport(app=app_with_dart), base_url="http://test",
        ) as ac:
            resp = await ac.post("/api/v1/company/enrich", json={"company_name": "테스트"})

        assert resp.status_code == 200
        data = resp.json()["data"]
        assert len(data["candidates"]) == 3
        assert dart.get_company_info.call_count == 3


class TestEnrichNoMatch:
    async def test_no_match_returns_empty(self, app_with_dart):
        dart = _make_mock_dart([])
        app_with_dart.dependency_overrides[get_dart_client] = lambda: dart

        async with AsyncClient(
            transport=ASGITransport(app=app_with_dart), base_url="http://test",
        ) as ac:
            resp = await ac.post("/api/v1/company/enrich", json={"company_name": "없는회사"})

        data = resp.json()["data"]
        assert data["matched"] is False
        assert data["company"] is None
        assert data["candidates"] == []


class TestEnrichErrors:
    async def test_no_dart_key_returns_502(self):
        app = FastAPI()
        register_exception_handlers(app)
        app.include_router(router)
        app.dependency_overrides = {
            get_tenant_id: lambda: TENANT_ID,
            get_dart_client: lambda: None,
        }

        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test",
        ) as ac:
            resp = await ac.post("/api/v1/company/enrich", json={"company_name": "테스트"})

        assert resp.status_code == 502

    async def test_empty_name_returns_422(self, ac):
        resp = await ac.post("/api/v1/company/enrich", json={"company_name": ""})
        assert resp.status_code == 400

    async def test_missing_tenant_returns_401(self):
        app = FastAPI()
        register_exception_handlers(app)
        app.include_router(router)
        app.dependency_overrides = {
            get_dart_client: lambda: _make_mock_dart([]),
        }

        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test",
        ) as ac:
            resp = await ac.post("/api/v1/company/enrich", json={"company_name": "테스트"})

        assert resp.status_code == 401
