"""DartClient 단위 테스트."""
import io
import zipfile

import pytest

from app.clients.dart import (
    CorpInfo,
    DartClient,
    DartDisclosureItem,
    _clean,
    _extract_text_from_html,
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

    @pytest.mark.asyncio
    async def test_returns_none_on_exception(self):
        from unittest.mock import AsyncMock, patch

        client = self._make_client()
        with patch("app.clients.dart.httpx.AsyncClient") as mock_cls:
            ctx = AsyncMock()
            ctx.get = AsyncMock(side_effect=Exception("network error"))
            mock_cls.return_value.__aenter__ = AsyncMock(return_value=ctx)
            mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

            result = await client.fetch_document("20260515000001")

        assert result is None
