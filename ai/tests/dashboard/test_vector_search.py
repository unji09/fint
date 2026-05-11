from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import numpy as np
import pytest

from app.dashboard.vector_search import semantic_search
from app.schemas.dashboard import SemanticSearchSpec


def _mock_embedder(dim: int = 384) -> MagicMock:
    embedder = MagicMock()
    embedder.dimension = dim
    embedder.embed_query.return_value = np.random.default_rng(0).standard_normal(dim).astype(np.float32)
    return embedder


def _mock_db_with_results(rows: list[dict]) -> AsyncMock:
    db = AsyncMock()
    result = MagicMock()
    result.mappings.return_value.all.return_value = rows
    db.execute.return_value = result
    return db


class TestSemanticSearch:
    @pytest.mark.asyncio
    async def test_returns_search_results(self):
        embedder = _mock_embedder()
        db = _mock_db_with_results(
            [
                {
                    "news_article_id": 1,
                    "title": "삼성전자 반도체",
                    "content_summary": "삼성 반도체 요약",
                    "publisher": "한경",
                    "published_at": "2025-01-01",
                    "link": "https://example.com/1",
                    "title_score": 0.9,
                    "summary_score": 0.85,
                }
            ]
        )
        spec = SemanticSearchSpec(search_text="삼성전자 반도체", top_k=5)

        results = await semantic_search(spec, embedder=embedder, db=db)

        assert len(results) == 1
        assert results[0].document_title == "삼성전자 반도체"
        assert results[0].score > 0

    @pytest.mark.asyncio
    async def test_returns_empty_when_no_embedder(self):
        db = _mock_db_with_results([])
        spec = SemanticSearchSpec(search_text="test", top_k=5)

        results = await semantic_search(spec, embedder=None, db=db)

        assert results == []

    @pytest.mark.asyncio
    async def test_calls_embedder_with_query(self):
        embedder = _mock_embedder()
        db = _mock_db_with_results([])
        spec = SemanticSearchSpec(search_text="반도체 투자", top_k=10)

        await semantic_search(spec, embedder=embedder, db=db)

        embedder.embed_query.assert_called_once_with("반도체 투자")

    @pytest.mark.asyncio
    async def test_respects_top_k(self):
        embedder = _mock_embedder()
        db = _mock_db_with_results([])
        spec = SemanticSearchSpec(search_text="test", top_k=7)

        await semantic_search(spec, embedder=embedder, db=db)

        call_args = db.execute.call_args
        sql_text = str(call_args[0][0])
        assert "7" in str(call_args[1]) or ":top_k" in sql_text

    @pytest.mark.asyncio
    async def test_search_result_fields(self):
        embedder = _mock_embedder()
        db = _mock_db_with_results(
            [
                {
                    "news_article_id": 42,
                    "title": "테스트 뉴스",
                    "content_summary": "뉴스 요약",
                    "publisher": "매경",
                    "published_at": "2025-03-15",
                    "link": "https://example.com/42",
                    "title_score": 0.8,
                    "summary_score": 0.75,
                }
            ]
        )
        spec = SemanticSearchSpec(search_text="test", top_k=5)

        results = await semantic_search(spec, embedder=embedder, db=db)

        r = results[0]
        assert r.document_title == "테스트 뉴스"
        assert r.source == "매경"
        assert r.chunk_text == "뉴스 요약"
