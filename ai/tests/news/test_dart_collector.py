"""DartCollector 단위 테스트."""
from unittest.mock import AsyncMock, MagicMock

import numpy as np
import pytest

from app.clients.dart import CorpInfo, DartClient, DartDisclosureItem
from app.news.dart_collector import DartCollector
from app.news.naver_collector import AccountInfo


def _make_item(rcept_no: str = "20260515000001") -> DartDisclosureItem:
    return DartDisclosureItem(
        corp_code="00126380",
        corp_name="삼성전자",
        stock_code="005930",
        corp_cls="Y",
        report_nm="분기보고서 (2026.03)",
        rcept_no=rcept_no,
        flr_nm="삼성전자",
        rcept_dt="20260515",
        rm="",
    )


def _mock_embedder(dim: int = 384):
    embedder = MagicMock()
    embedder.embed_passages = MagicMock(
        side_effect=lambda texts: np.random.randn(len(texts), dim).astype(np.float32)
    )
    return embedder


def _mock_dart_client(
    items: list[DartDisclosureItem] | None = None,
    doc_content: str | None = "공시 본문 텍스트입니다. 삼성전자 분기보고서 상세 내용.",
):
    client = MagicMock(spec=DartClient)
    client.ensure_corp_codes = AsyncMock()
    client.find_corp = MagicMock(
        return_value=CorpInfo("00126380", "삼성전자", "005930"),
    )
    client.list_disclosures = AsyncMock(return_value=items or [])
    client.fetch_document = AsyncMock(return_value=doc_content)
    return client


def _mock_db():
    return AsyncMock()


def _make_fetch_result(rows):
    result = MagicMock()
    result.fetchall.return_value = rows
    return result


ACCOUNT = AccountInfo(account_id=1, name="삼성전자")
ACCOUNT_B = AccountInfo(account_id=2, name="LG전자")


class TestDartCollectForAccounts:
    @pytest.mark.asyncio
    async def test_no_items_returns_empty(self):
        dart = _mock_dart_client([])
        embedder = _mock_embedder()
        db = _mock_db()

        collector = DartCollector(dart, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert result.new_disclosures == []
        assert result.existing_rcept_nos == {}

    @pytest.mark.asyncio
    async def test_new_disclosure_returned_with_content_and_embedding(self):
        item = _make_item("20260515000001")
        dart = _mock_dart_client([item])
        embedder = _mock_embedder()
        db = _mock_db()
        db.execute = AsyncMock(return_value=_make_fetch_result([]))

        collector = DartCollector(dart, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert len(result.new_disclosures) == 1
        d = result.new_disclosures[0]
        assert d.item.rcept_no == "20260515000001"
        assert d.account_ids == [1]
        assert d.content is not None
        assert "공시 본문" in d.content
        assert d.content_summary is not None
        assert "[정기보고서]" in d.content_summary
        assert len(d.title_embedding) == 384
        assert len(d.summary_embedding) == 384
        dart.fetch_document.assert_called_once_with("20260515000001")
        embedder.embed_passages.assert_called()

    @pytest.mark.asyncio
    async def test_existing_disclosure_returned_as_existing(self):
        item = _make_item("20260515000001")
        dart = _mock_dart_client([item])
        embedder = _mock_embedder()
        db = _mock_db()
        db.execute = AsyncMock(
            return_value=_make_fetch_result([("20260515000001",)])
        )

        collector = DartCollector(dart, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert result.new_disclosures == []
        assert "20260515000001" in result.existing_rcept_nos
        assert result.existing_rcept_nos["20260515000001"] == [1]

    @pytest.mark.asyncio
    async def test_fetch_document_failure_uses_signal_summary(self):
        item = _make_item("20260515000001")
        dart = _mock_dart_client([item], doc_content=None)
        embedder = _mock_embedder()
        db = _mock_db()
        db.execute = AsyncMock(return_value=_make_fetch_result([]))

        collector = DartCollector(dart, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert len(result.new_disclosures) == 1
        d = result.new_disclosures[0]
        assert d.content is None
        assert d.content_summary is not None
        assert "[정기보고서]" in d.content_summary
        assert len(d.title_embedding) == 384
        assert len(d.summary_embedding) == 384

    @pytest.mark.asyncio
    async def test_no_corp_code_skips_account(self):
        dart = _mock_dart_client()
        dart.find_corp = MagicMock(return_value=None)
        embedder = _mock_embedder()
        db = _mock_db()

        collector = DartCollector(dart, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert result.new_disclosures == []
        dart.list_disclosures.assert_not_called()

    @pytest.mark.asyncio
    async def test_skip_largest_shareholder_change_report(self):
        """최대주주등소유주식변동신고서는 수집에서 제외."""
        item = DartDisclosureItem(
            corp_code="00126380",
            corp_name="삼성전자",
            stock_code="005930",
            corp_cls="Y",
            report_nm="최대주주등소유주식변동신고서",
            rcept_no="20260515000099",
            flr_nm="삼성전자",
            rcept_dt="20260515",
            rm="",
        )
        dart = _mock_dart_client([item])
        embedder = _mock_embedder()
        db = _mock_db()
        db.execute = AsyncMock(return_value=_make_fetch_result([]))

        collector = DartCollector(dart, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert result.new_disclosures == []
        assert result.existing_rcept_nos == {}
        dart.fetch_document.assert_not_called()

    @pytest.mark.asyncio
    async def test_same_disclosure_found_by_two_accounts_merged(self):
        item = _make_item("20260515000001")
        dart = MagicMock(spec=DartClient)
        dart.ensure_corp_codes = AsyncMock()
        dart.find_corp = MagicMock(
            return_value=CorpInfo("00126380", "삼성전자", "005930"),
        )
        dart.list_disclosures = AsyncMock(return_value=[item])
        dart.fetch_document = AsyncMock(return_value="본문 내용")
        embedder = _mock_embedder()
        db = _mock_db()
        db.execute = AsyncMock(return_value=_make_fetch_result([]))

        collector = DartCollector(dart, embedder)
        result = await collector.collect_for_accounts([ACCOUNT, ACCOUNT_B], db)

        assert len(result.new_disclosures) == 1
        assert sorted(result.new_disclosures[0].account_ids) == [1, 2]
        dart.fetch_document.assert_called_once()
