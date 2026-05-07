"""pgvector 시맨틱 검색 — 스켈레톤. Phase 0 (204) 완료 후 구현."""

from __future__ import annotations

from dataclasses import dataclass

from app.schemas.dashboard import SemanticSearchSpec


@dataclass
class SearchResult:
    chunk_text: str
    document_title: str
    source: str
    score: float


async def semantic_search(
    spec: SemanticSearchSpec,
    *,
    tenant_id: int,
) -> list[SearchResult]:
    """비정형 데이터 벡터 검색. 현재는 빈 결과 반환 (임베딩 미적재)."""
    return []
