"""NaverNewsCollector 단위 테스트."""
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, call

import numpy as np
import pytest

from app.clients.naver import NaverNewsItem
from app.news.naver_collector import AccountInfo, NaverNewsCollector


def _make_item(link: str = "https://n.news.naver.com/1") -> NaverNewsItem:
    return NaverNewsItem(
        title="삼성전자 실적 발표",
        original_link="https://example.com/orig",
        link=link,
        description="삼성전자가 1분기 실적을 발표했다",
        pub_date=datetime(2025, 5, 12, tzinfo=timezone.utc),
    )


def _mock_embedder(dim: int = 384):
    embedder = MagicMock()
    embedder.dimension = dim
    embedder.embed_passages = MagicMock(
        side_effect=lambda texts: np.random.randn(len(texts), dim).astype(np.float32)
    )
    return embedder


def _mock_naver_client(items: list[NaverNewsItem] | None = None):
    client = MagicMock()
    client.search = AsyncMock(return_value=items or [])
    return client


def _mock_db():
    db = AsyncMock()
    return db


def _make_fetch_result(rows):
    result = MagicMock()
    result.fetchall.return_value = rows
    return result


ACCOUNT = AccountInfo(account_id=1, name="삼성전자")
ACCOUNT_B = AccountInfo(account_id=2, name="LG전자")


class TestCollectForAccounts:
    @pytest.mark.asyncio
    async def test_no_items_returns_empty(self):
        naver = _mock_naver_client([])
        embedder = _mock_embedder()
        db = _mock_db()

        collector = NaverNewsCollector(naver, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert result.new_articles == []
        assert result.existing_links == {}

    @pytest.mark.asyncio
    async def test_new_item_returned_with_embedding(self):
        item = _make_item("https://n.news.naver.com/new1")
        naver = _mock_naver_client([item])
        embedder = _mock_embedder()
        db = _mock_db()

        db.execute = AsyncMock(
            return_value=_make_fetch_result([])
        )

        collector = NaverNewsCollector(naver, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert len(result.new_articles) == 1
        article = result.new_articles[0]
        assert article.item.link == "https://n.news.naver.com/new1"
        assert article.account_ids == [1]
        assert len(article.title_embedding) == 384
        assert len(article.summary_embedding) == 384
        assert result.existing_links == {}
        embedder.embed_passages.assert_called()

    @pytest.mark.asyncio
    async def test_existing_item_returned_as_existing_link(self):
        item = _make_item("https://n.news.naver.com/existing")
        naver = _mock_naver_client([item])
        embedder = _mock_embedder()
        db = _mock_db()

        db.execute = AsyncMock(
            return_value=_make_fetch_result([("https://n.news.naver.com/existing",)])
        )

        collector = NaverNewsCollector(naver, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert result.new_articles == []
        assert "https://n.news.naver.com/existing" in result.existing_links
        assert result.existing_links["https://n.news.naver.com/existing"] == [1]

    @pytest.mark.asyncio
    async def test_empty_accounts_returns_empty(self):
        naver = _mock_naver_client()
        embedder = _mock_embedder()
        db = _mock_db()

        collector = NaverNewsCollector(naver, embedder)
        result = await collector.collect_for_accounts([], db)

        assert result.new_articles == []
        assert result.existing_links == {}
        naver.search.assert_not_called()

    @pytest.mark.asyncio
    async def test_same_article_found_by_two_accounts_merged(self):
        item = _make_item("https://n.news.naver.com/shared")
        naver_client = MagicMock()
        naver_client.search = AsyncMock(return_value=[item])
        embedder = _mock_embedder()
        db = _mock_db()

        db.execute = AsyncMock(
            return_value=_make_fetch_result([])
        )

        collector = NaverNewsCollector(naver_client, embedder)
        result = await collector.collect_for_accounts([ACCOUNT, ACCOUNT_B], db)

        assert len(result.new_articles) == 1
        assert sorted(result.new_articles[0].account_ids) == [1, 2]


class TestExistingLinkMerge:
    @pytest.mark.asyncio
    async def test_existing_link_merges_account_ids(self):
        item = _make_item("https://n.news.naver.com/existing")
        naver_client = MagicMock()
        naver_client.search = AsyncMock(return_value=[item])
        embedder = _mock_embedder()
        db = _mock_db()

        db.execute = AsyncMock(
            return_value=_make_fetch_result([("https://n.news.naver.com/existing",)])
        )

        collector = NaverNewsCollector(naver_client, embedder)
        result = await collector.collect_for_accounts([ACCOUNT, ACCOUNT_B], db)

        assert result.new_articles == []
        link_accounts = result.existing_links["https://n.news.naver.com/existing"]
        assert sorted(link_accounts) == [1, 2]


def _fixed_embedder(seed: int = 42, dim: int = 384):
    """매번 동일한 정규화 임베딩을 반환하는 mock embedder."""
    rng = np.random.RandomState(seed)
    vec = rng.randn(dim).astype(np.float32)
    vec = vec / np.linalg.norm(vec)

    embedder = MagicMock()
    embedder.dimension = dim
    embedder.embed_passages = MagicMock(
        side_effect=lambda texts: np.tile(vec, (len(texts), 1)),
    )
    return embedder, vec


class TestDbDedup:
    """DB 기존 기사 대비 의미적 중복 제거 통합 테스트."""

    @pytest.mark.asyncio
    async def test_article_filtered_when_db_has_similar(self):
        """DB에 유사 임베딩이 있으면 신규 기사에서 제거한다."""
        item = _make_item("https://n.news.naver.com/new1")
        naver = _mock_naver_client([item])
        embedder, vec = _fixed_embedder(42)
        db = _mock_db()

        link_check = _make_fetch_result([])
        embedding_fetch = _make_fetch_result([(vec.tolist(),)])
        db.execute = AsyncMock(side_effect=[link_check, embedding_fetch])

        collector = NaverNewsCollector(naver, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert len(result.new_articles) == 0

    @pytest.mark.asyncio
    async def test_article_kept_when_db_has_no_similar(self):
        """DB 기사와 유사도가 낮으면 신규 기사를 유지한다."""
        item = _make_item("https://n.news.naver.com/new1")
        naver = _mock_naver_client([item])
        embedder, vec = _fixed_embedder(42)
        db = _mock_db()

        different_vec = np.random.RandomState(99).randn(384).astype(np.float32)
        different_vec = different_vec / np.linalg.norm(different_vec)

        link_check = _make_fetch_result([])
        embedding_fetch = _make_fetch_result([(different_vec.tolist(),)])
        db.execute = AsyncMock(side_effect=[link_check, embedding_fetch])

        collector = NaverNewsCollector(naver, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert len(result.new_articles) == 1

    @pytest.mark.asyncio
    async def test_article_kept_when_db_empty(self):
        """DB에 최근 기사가 없으면 모든 신규 기사를 유지한다."""
        item = _make_item("https://n.news.naver.com/new1")
        naver = _mock_naver_client([item])
        embedder, _ = _fixed_embedder(42)
        db = _mock_db()

        link_check = _make_fetch_result([])
        embedding_fetch = _make_fetch_result([])
        db.execute = AsyncMock(side_effect=[link_check, embedding_fetch])

        collector = NaverNewsCollector(naver, embedder)
        result = await collector.collect_for_accounts([ACCOUNT], db)

        assert len(result.new_articles) == 1