"""DartClient 단위 테스트."""
import io
import zipfile
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.clients.dart import (
    CompanyInfo,
    CorpInfo,
    DartClient,
    DartDisclosureItem,
    _clean,
    _extract_text_from_html,
    _normalize_corp_name,
    _transliterate_english,
)


class TestClean:
    def test_strips_whitespace(self):
        assert _clean("  삼성전자  ") == "삼성전자"

    def test_none_returns_empty(self):
        assert _clean(None) == ""

    def test_empty_returns_empty(self):
        assert _clean("") == ""


class TestFindCorp:
    def _make_client_with_map(self, corp_map):
        client = DartClient.__new__(DartClient)
        client._api_key = "test"
        client._corp_map = corp_map
        return client

    def test_exact_match(self):
        client = self._make_client_with_map({
            "삼성전자": [CorpInfo("00126380", "삼성전자", "005930")],
        })
        result = client.find_corp("삼성전자")
        assert result is not None
        assert result.corp_code == "00126380"

    def test_no_match_returns_none(self):
        client = self._make_client_with_map({
            "삼성전자": [CorpInfo("00126380", "삼성전자", "005930")],
        })
        assert client.find_corp("LG전자") is None

    def test_prefers_listed_company(self):
        client = self._make_client_with_map({
            "테스트": [
                CorpInfo("00000001", "테스트", ""),
                CorpInfo("00000002", "테스트", "123456"),
            ],
        })
        result = client.find_corp("테스트")
        assert result is not None
        assert result.corp_code == "00000002"

    def test_unloaded_map_returns_none(self):
        client = self._make_client_with_map(None)
        assert client.find_corp("삼성전자") is None


class TestDartDisclosureItem:
    def test_frozen_dataclass(self):
        item = DartDisclosureItem(
            corp_code="00126380",
            corp_name="삼성전자",
            stock_code="005930",
            corp_cls="Y",
            report_nm="분기보고서",
            rcept_no="20260515000001",
            flr_nm="삼성전자",
            rcept_dt="20260515",
            rm="",
        )
        assert item.rcept_no == "20260515000001"


class TestExtractTextFromHtml:
    def test_strips_tags(self):
        assert _extract_text_from_html("<p>Hello <b>World</b></p>") == "Hello World"

    def test_skips_script_and_style(self):
        html = "<p>본문</p><script>var x = 1;</script><style>.a{}</style><p>끝</p>"
        result = _extract_text_from_html(html)
        assert "본문" in result
        assert "끝" in result
        assert "var x" not in result

    def test_empty_returns_empty(self):
        assert _extract_text_from_html("") == ""

    def test_collapses_whitespace(self):
        assert _extract_text_from_html("<p>a   \n\n   b</p>") == "a b"

    def test_plain_text_unchanged(self):
        assert _extract_text_from_html("그냥 텍스트") == "그냥 텍스트"


def _make_zip_with_html(html_content: str, filename: str = "doc.html") -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr(filename, html_content)
    return buf.getvalue()


class TestFetchDocument:
    def _make_client(self):
        client = DartClient.__new__(DartClient)
        client._api_key = "test"
        client._corp_map = None
        return client

    @pytest.mark.asyncio
    async def test_extracts_text_from_zip(self):
        from unittest.mock import AsyncMock, MagicMock, patch

        zip_bytes = _make_zip_with_html("<p>공시 본문 내용입니다</p>")
        mock_resp = MagicMock()
        mock_resp.content = zip_bytes
        mock_resp.raise_for_status = MagicMock()
        mock_resp.headers = {"content-type": "application/zip"}

        client = self._make_client()
        with patch("app.clients.dart.httpx.AsyncClient") as mock_cls:
            ctx = AsyncMock()
            ctx.get = AsyncMock(return_value=mock_resp)
            mock_cls.return_value.__aenter__ = AsyncMock(return_value=ctx)
            mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

            result = await client.fetch_document("20260515000001")

        assert result is not None
        assert "공시 본문 내용입니다" in result

    @pytest.mark.asyncio
    async def test_returns_none_on_xml_error_response(self):
        from unittest.mock import AsyncMock, MagicMock, patch

        mock_resp = MagicMock()
        mock_resp.content = b"<error>not found</error>"
        mock_resp.raise_for_status = MagicMock()
        mock_resp.headers = {"content-type": "application/xml"}

        client = self._make_client()
        with patch("app.clients.dart.httpx.AsyncClient") as mock_cls:
            ctx = AsyncMock()
            ctx.get = AsyncMock(return_value=mock_resp)
            mock_cls.return_value.__aenter__ = AsyncMock(return_value=ctx)
            mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

            result = await client.fetch_document("invalid")

        assert result is None


# ---------------------------------------------------------------------------
# 헬퍼 함수 테스트
# ---------------------------------------------------------------------------


class TestNormalizeCorpName:
    def test_strips_ju(self):
        assert _normalize_corp_name("(주)삼성전자") == "삼성전자"

    def test_strips_jusik(self):
        assert _normalize_corp_name("주식회사 카카오") == "카카오"

    def test_strips_yu(self):
        assert _normalize_corp_name("(유)테스트") == "테스트"

    def test_strips_trailing_ju(self):
        assert _normalize_corp_name("삼성에스디에스(주)") == "삼성에스디에스"

    def test_strips_whitespace(self):
        assert _normalize_corp_name("  삼성전자  ") == "삼성전자"

    def test_no_change_for_plain_name(self):
        assert _normalize_corp_name("삼성전자") == "삼성전자"


class TestTransliterateEnglish:
    def test_sds(self):
        assert _transliterate_english("삼성SDS") == "삼성에스디에스"

    def test_lg_cns(self):
        assert _transliterate_english("LG CNS") == "엘지씨엔에스"

    def test_case_insensitive(self):
        assert _transliterate_english("삼성sds") == "삼성에스디에스"

    def test_no_english_unchanged(self):
        assert _transliterate_english("삼성전자") == "삼성전자"

    def test_hd(self):
        assert _transliterate_english("HD현대") == "에이치디현대"

    def test_sk(self):
        assert _transliterate_english("SK하이닉스") == "에스케이하이닉스"


# ---------------------------------------------------------------------------
# find_corp_fuzzy 테스트
# ---------------------------------------------------------------------------


class TestFindCorpFuzzy:
    """find_corp_fuzzy() 는 list[CorpInfo] 를 반환한다."""

    def _make_client_with_map(self, corp_map):
        client = DartClient.__new__(DartClient)
        client._api_key = "test"
        client._corp_map = corp_map
        return client

    def test_exact_match_returns_single(self):
        client = self._make_client_with_map({
            "삼성전자": [CorpInfo("00126380", "삼성전자", "005930")],
        })
        result = client.find_corp_fuzzy("삼성전자")
        assert len(result) == 1
        assert result[0].corp_code == "00126380"

    def test_normalized_match(self):
        client = self._make_client_with_map({
            "삼성전자": [CorpInfo("00126380", "삼성전자", "005930")],
        })
        result = client.find_corp_fuzzy("(주)삼성전자")
        assert len(result) == 1
        assert result[0].corp_name == "삼성전자"

    def test_transliteration_match(self):
        client = self._make_client_with_map({
            "삼성에스디에스": [CorpInfo("00126186", "삼성에스디에스", "018260")],
        })
        result = client.find_corp_fuzzy("삼성SDS")
        assert len(result) == 1
        assert result[0].corp_code == "00126186"

    def test_lg_cns_transliteration(self):
        client = self._make_client_with_map({
            "엘지씨엔에스": [CorpInfo("00139834", "엘지씨엔에스", "064400")],
        })
        result = client.find_corp_fuzzy("LG CNS")
        assert len(result) == 1
        assert result[0].corp_code == "00139834"

    def test_dart_mixed_name_transliterated(self):
        """DART에 영문+한글 혼합명(LG씨엔에스)으로 등록된 경우."""
        client = self._make_client_with_map({
            "LG씨엔에스": [CorpInfo("00139834", "LG씨엔에스", "064400")],
            "씨엔에스": [CorpInfo("00338408", "씨엔에스", "")],
        })
        result = client.find_corp_fuzzy("LG CNS")
        assert len(result) == 1
        assert result[0].corp_code == "00139834"

    def test_fallback_scan_multiple_candidates(self):
        """입력이 여러 DART 기업에 포함되면 전부 후보로 반환."""
        client = self._make_client_with_map({
            "현대자동차": [CorpInfo("00164742", "현대자동차", "005380")],
            "현대건설": [CorpInfo("00164779", "현대건설", "000720")],
            "현대백화점": [CorpInfo("00164791", "현대백화점", "069960")],
        })
        result = client.find_corp_fuzzy("현대자동")
        assert len(result) >= 1
        codes = {r.corp_code for r in result}
        assert "00164742" in codes

    def test_no_match_returns_empty(self):
        client = self._make_client_with_map({
            "삼성전자": [CorpInfo("00126380", "삼성전자", "005930")],
        })
        result = client.find_corp_fuzzy("완전없는회사")
        assert result == []

    def test_unloaded_map_returns_empty(self):
        client = self._make_client_with_map(None)
        result = client.find_corp_fuzzy("삼성전자")
        assert result == []

    def test_short_input_no_scan(self):
        """2자 미만 입력은 4단계 순회 진입 안 함."""
        client = self._make_client_with_map({
            "현대자동차": [CorpInfo("00164742", "현대자동차", "005380")],
        })
        result = client.find_corp_fuzzy("현")
        assert result == []

    def test_prefers_listed_in_exact(self):
        client = self._make_client_with_map({
            "테스트": [
                CorpInfo("00000001", "테스트", ""),
                CorpInfo("00000002", "테스트", "123456"),
            ],
        })
        result = client.find_corp_fuzzy("테스트")
        assert len(result) == 1
        assert result[0].stock_code == "123456"


# ---------------------------------------------------------------------------
# get_company_info 테스트
# ---------------------------------------------------------------------------


class TestGetCompanyInfo:
    def _make_client(self):
        client = DartClient.__new__(DartClient)
        client._api_key = "test"
        client._corp_map = None
        return client

    @pytest.mark.asyncio
    async def test_returns_company_info_on_success(self):
        mock_json = {
            "status": "000",
            "corp_code": "00126186",
            "corp_name": "삼성에스디에스(주)",
            "ceo_nm": "이준희",
            "bizr_no": "1108128774",
            "induty_code": "62021",
            "adres": "서울특별시 송파구",
            "phn_no": "02-6155-3114",
            "est_dt": "19850501",
        }
        mock_resp = MagicMock()
        mock_resp.json.return_value = mock_json
        mock_resp.raise_for_status = MagicMock()

        client = self._make_client()
        with patch("app.clients.dart.httpx.AsyncClient") as mock_cls:
            ctx = AsyncMock()
            ctx.get = AsyncMock(return_value=mock_resp)
            mock_cls.return_value.__aenter__ = AsyncMock(return_value=ctx)
            mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

            result = await client.get_company_info("00126186")

        assert result is not None
        assert isinstance(result, CompanyInfo)
        assert result.bizr_no == "1108128774"
        assert result.corp_name == "삼성에스디에스(주)"
        assert result.induty_code == "62021"

    @pytest.mark.asyncio
    async def test_returns_none_on_api_error(self):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"status": "013", "message": "no data"}
        mock_resp.raise_for_status = MagicMock()

        client = self._make_client()
        with patch("app.clients.dart.httpx.AsyncClient") as mock_cls:
            ctx = AsyncMock()
            ctx.get = AsyncMock(return_value=mock_resp)
            mock_cls.return_value.__aenter__ = AsyncMock(return_value=ctx)
            mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

            result = await client.get_company_info("99999999")

        assert result is None

    @pytest.mark.asyncio
    async def test_returns_none_on_network_error(self):
        client = self._make_client()
        with patch("app.clients.dart.httpx.AsyncClient") as mock_cls:
            ctx = AsyncMock()
            ctx.get = AsyncMock(side_effect=Exception("timeout"))
            mock_cls.return_value.__aenter__ = AsyncMock(return_value=ctx)
            mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

            result = await client.get_company_info("00126186")

        assert result is None
