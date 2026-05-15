from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from sqlalchemy import text

from app.schemas.dashboard import SemanticSearchSpec

if TYPE_CHECKING:
    from sqlalchemy.ext.asyncio import AsyncSession

    from app.clients.embedder import EmbedderClient


@dataclass
class SearchResult:
    chunk_text: str
    document_title: str
    source: str
    score: float
    link: str | None = None
    published_at: str | None = None


_MIN_SIMILARITY_SCORE = 0.3

_NEWS_SEARCH_SQL = text("""
    SELECT * FROM (
        SELECT DISTINCT ON (n.news_article_id)
            n.news_article_id,
            n.title,
            n.content_summary,
            n.publisher,
            n.published_at::text,
            n.link,
            1 - (n.title_embedding <=> CAST(:query_vec AS vector)) AS title_score,
            1 - (n.summary_embedding <=> CAST(:query_vec AS vector)) AS summary_score
        FROM news_articles n
        JOIN account_news_articles ana ON n.news_article_id = ana.news_article_id
        JOIN accounts a ON ana.account_id = a.account_id
        JOIN account_user_assignment aua ON a.account_id = aua.account_id
        JOIN users u ON aua.user_id = u.user_id
        WHERE n.title_embedding IS NOT NULL AND u.tenant_id = :tenant_id
            AND GREATEST(
                1 - (n.title_embedding <=> CAST(:query_vec AS vector)),
                1 - (n.summary_embedding <=> CAST(:query_vec AS vector))
            ) >= :min_score
        ORDER BY n.news_article_id,
            GREATEST(
                1 - (n.title_embedding <=> CAST(:query_vec AS vector)),
                1 - (n.summary_embedding <=> CAST(:query_vec AS vector))
            ) DESC
    ) sub
    ORDER BY GREATEST(sub.title_score, sub.summary_score) DESC
    LIMIT :top_k
""")

_DART_SEARCH_SQL = text("""
    SELECT * FROM (
        SELECT DISTINCT ON (d.dart_disclosure_id)
            d.dart_disclosure_id,
            d.report_nm,
            d.content_summary,
            d.corp_name,
            d.rcept_dt,
            d.rcept_no,
            1 - (d.title_embedding <=> CAST(:query_vec AS vector)) AS title_score,
            1 - (d.summary_embedding <=> CAST(:query_vec AS vector)) AS summary_score
        FROM dart_disclosures d
        JOIN account_dart_disclosures add_ ON d.dart_disclosure_id = add_.dart_disclosure_id
        JOIN accounts a ON add_.account_id = a.account_id
        JOIN account_user_assignment aua ON a.account_id = aua.account_id
        JOIN users u ON aua.user_id = u.user_id
        WHERE d.title_embedding IS NOT NULL AND u.tenant_id = :tenant_id
            AND GREATEST(
                1 - (d.title_embedding <=> CAST(:query_vec AS vector)),
                1 - (d.summary_embedding <=> CAST(:query_vec AS vector))
            ) >= :min_score
        ORDER BY d.dart_disclosure_id,
            GREATEST(
                1 - (d.title_embedding <=> CAST(:query_vec AS vector)),
                1 - (d.summary_embedding <=> CAST(:query_vec AS vector))
            ) DESC
    ) sub
    ORDER BY GREATEST(sub.title_score, sub.summary_score) DESC
    LIMIT :top_k
""")

_DART_VIEWER_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo="


def _best_score(row: dict) -> float:
    return max(float(row.get("title_score") or 0), float(row.get("summary_score") or 0))


def _news_row_to_result(row: dict) -> SearchResult:
    return SearchResult(
        chunk_text=row.get("content_summary", "") or "",
        document_title=row["title"],
        source=row["publisher"],
        score=_best_score(row),
        link=row.get("link"),
        published_at=row.get("published_at"),
    )


def _dart_row_to_result(row: dict) -> SearchResult:
    return SearchResult(
        chunk_text=row.get("content_summary", "") or "",
        document_title=row["report_nm"],
        source=row["corp_name"],
        score=_best_score(row),
        link=_DART_VIEWER_URL + str(row["rcept_no"]),
        published_at=row.get("rcept_dt"),
    )


async def semantic_search(
    spec: SemanticSearchSpec,
    *,
    tenant_id: int,
    embedder: EmbedderClient | None = None,
    db: AsyncSession | None = None,
) -> list[SearchResult]:
    if embedder is None or db is None:
        return []

    query_vec = embedder.embed_query(spec.search_text)
    vec_str = "[" + ",".join(f"{v:.6f}" for v in query_vec.tolist()) + "]"
    params = {
        "query_vec": vec_str,
        "top_k": spec.top_k,
        "tenant_id": tenant_id,
        "min_score": spec.min_score,
    }

    source = spec.source_filter

    if source == "NEWS":
        result = await db.execute(_NEWS_SEARCH_SQL, params)
        return [_news_row_to_result(row) for row in result.mappings().all()]

    if source == "DART":
        result = await db.execute(_DART_SEARCH_SQL, params)
        return [_dart_row_to_result(row) for row in result.mappings().all()]

    news_raw = await db.execute(_NEWS_SEARCH_SQL, params)
    dart_raw = await db.execute(_DART_SEARCH_SQL, params)
    news_rows = [_news_row_to_result(row) for row in news_raw.mappings().all()]
    dart_rows = [_dart_row_to_result(row) for row in dart_raw.mappings().all()]

    merged = sorted(news_rows + dart_rows, key=lambda r: r.score, reverse=True)
    return merged[: spec.top_k]
