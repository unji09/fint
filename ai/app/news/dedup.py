"""수집된 뉴스 기사의 의미적 중복 제거.

title_embedding 코사인 유사도 기반 클러스터링으로
같은 이슈의 다른 언론사 기사를 하나로 병합한다.
"""
from __future__ import annotations

import logging
from collections import defaultdict
from typing import TYPE_CHECKING

import numpy as np

if TYPE_CHECKING:
    from app.news.naver_collector import CollectedArticleData

logger = logging.getLogger(__name__)

SIMILARITY_THRESHOLD = 0.85


def deduplicate_articles(
    articles: list[CollectedArticleData],
) -> list[CollectedArticleData]:
    """중복 제거된 대표 기사 리스트를 반환한다."""
    if len(articles) <= 1:
        return articles

    if any(len(a.title_embedding) == 0 for a in articles):
        logger.warning("dedup skipped: some articles have empty embeddings")
        return articles

    embeddings = np.array([a.title_embedding for a in articles], dtype=np.float32)
    sim_matrix = embeddings @ embeddings.T

    n = len(articles)
    parent = list(range(n))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(x: int, y: int) -> None:
        rx, ry = find(x), find(y)
        if rx != ry:
            parent[rx] = ry

    for i in range(n):
        for j in range(i + 1, n):
            if articles[i].item.pub_date.date() != articles[j].item.pub_date.date():
                continue
            if sim_matrix[i, j] >= SIMILARITY_THRESHOLD:
                union(i, j)

    clusters: dict[int, list[int]] = defaultdict(list)
    for i in range(n):
        clusters[find(i)].append(i)

    results: list[CollectedArticleData] = []
    for indices in clusters.values():
        cluster = [articles[i] for i in indices]
        representative = _pick_representative(cluster)
        _merge_account_ids(representative, cluster)
        results.append(representative)

        if len(cluster) >= 5:
            sims = [sim_matrix[indices[i], indices[j]]
                    for i in range(len(indices)) for j in range(i + 1, len(indices))]
            logger.debug(
                "large cluster (%d articles): titles=%s, similarity_range=[%.3f, %.3f]",
                len(cluster),
                [a.item.title for a in cluster],
                min(sims) if sims else 0.0,
                max(sims) if sims else 0.0,
            )

    removed = len(articles) - len(results)
    if removed > 0:
        logger.info(
            "dedup: %d articles → %d unique (removed %d duplicates, %d clusters)",
            len(articles), len(results), removed, len(clusters),
        )

    return results


def deduplicate_against_existing(
    articles: list[CollectedArticleData],
    existing_embeddings: np.ndarray,
) -> list[CollectedArticleData]:
    """DB 기존 기사 임베딩과 비교하여 중복을 필터링한다.

    existing_embeddings: (N, dim) shape의 L2-정규화된 title 임베딩 배열.
    """
    if not articles or existing_embeddings.size == 0:
        return articles

    if any(len(a.title_embedding) == 0 for a in articles):
        logger.warning("DB dedup skipped: some articles have empty embeddings")
        return articles

    new_matrix = np.array(
        [a.title_embedding for a in articles], dtype=np.float32,
    )
    sim_matrix = new_matrix @ existing_embeddings.T
    max_sims = sim_matrix.max(axis=1)

    results: list[CollectedArticleData] = []
    removed = 0
    for i, article in enumerate(articles):
        if max_sims[i] < SIMILARITY_THRESHOLD:
            results.append(article)
        else:
            removed += 1
            logger.info(
                "DB dedup: filtered '%s' (max_similarity=%.3f)",
                article.item.title, float(max_sims[i]),
            )

    if removed > 0:
        logger.info(
            "DB dedup: %d articles → %d unique (removed %d DB duplicates)",
            len(articles), len(results), removed,
        )

    return results


def _pick_representative(cluster: list[CollectedArticleData]) -> CollectedArticleData:
    return max(cluster, key=lambda a: (
        len(a.item.description),
        len(a.account_ids),
        -a.item.pub_date.timestamp(),
    ))


def _merge_account_ids(
    representative: CollectedArticleData,
    cluster: list[CollectedArticleData],
) -> None:
    merged: set[int] = set()
    for article in cluster:
        merged.update(article.account_ids)
    representative.account_ids = sorted(merged)
