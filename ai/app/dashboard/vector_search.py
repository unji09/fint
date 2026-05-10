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


_SEARCH_SQL = text("""
    SELECT
        news_article_id,
        title,
        content_summary,
        publisher,
        published_at::text,
        link,
        1 - (title_embedding <=> :query_vec::vector) AS title_score,
        1 - (summary_embedding <=> :query_vec::vector) AS summary_score
    FROM news_articles
    WHERE title_embedding IS NOT NULL
    ORDER BY GREATEST(
        1 - (title_embedding <=> :query_vec::vector),
        1 - (summary_embedding <=> :query_vec::vector)
    ) DESC
    LIMIT :top_k
""")


async def semantic_search(
    spec: SemanticSearchSpec,
    *,
    embedder: EmbedderClient | None = None,
    db: AsyncSession | None = None,
) -> list[SearchResult]:
    if embedder is None or db is None:
        return []

    query_vec = embedder.embed_query(spec.search_text)
    vec_str = "[" + ",".join(f"{v:.6f}" for v in query_vec.tolist()) + "]"

    result = await db.execute(_SEARCH_SQL, {"query_vec": vec_str, "top_k": spec.top_k})
    rows = result.mappings().all()

    return [
        SearchResult(
            chunk_text=row.get("content_summary", "") or "",
            document_title=row["title"],
            source=row["publisher"],
            score=max(float(row.get("title_score", 0)), float(row.get("summary_score", 0))),
            link=row.get("link"),
            published_at=row.get("published_at"),
        )
        for row in rows
    ]
