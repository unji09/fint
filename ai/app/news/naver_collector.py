"""네이버 뉴스 검색 API 기반 고객사별 뉴스 수집기.

DB에는 READ만 수행한다 (중복 체크용). 수집된 기사 데이터를 반환하면
Spring 서버가 DB에 INSERT를 담당한다.
"""
from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass, field

import numpy as np
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.clients.embedder import EmbedderClient
from app.clients.naver import NaverNewsClient, NaverNewsItem
from app.news.dedup import deduplicate_articles, deduplicate_against_existing

logger = logging.getLogger(__name__)

_INTER_REQUEST_DELAY = 0.1


@dataclass
class AccountInfo:
    account_id: int
    name: str


@dataclass
class CollectedArticleData:
    item: NaverNewsItem
    title_embedding: list[float]
    summary_embedding: list[float]
    account_ids: list[int]


@dataclass
class NaverCollectResult:
    new_articles: list[CollectedArticleData] = field(default_factory=list)
    existing_links: dict[str, list[int]] = field(default_factory=dict)


class NaverNewsCollector:
    def __init__(
        self,
        naver_client: NaverNewsClient,
        embedder: EmbedderClient,
    ) -> None:
        self._naver = naver_client
        self._embedder = embedder

    async def collect_for_accounts(
        self,
        accounts: list[AccountInfo],
        db: AsyncSession,
    ) -> NaverCollectResult:
        new_items_map: dict[str, tuple[NaverNewsItem, list[int]]] = {}
        existing_links_map: dict[str, list[int]] = {}

        for i, account in enumerate(accounts):
            try:
                items = await self._naver.search(query=account.name)
                if not items:
                    continue

                existing_links = await self._get_existing_links(
                    [item.link for item in items], db,
                )

                for item in items:
                    if item.link in existing_links:
                        acct_list = existing_links_map.setdefault(item.link, [])
                        if account.account_id not in acct_list:
                            acct_list.append(account.account_id)
                    elif item.link in new_items_map:
                        if account.account_id not in new_items_map[item.link][1]:
                            new_items_map[item.link][1].append(account.account_id)
                    else:
                        new_items_map[item.link] = (item, [account.account_id])

                logger.info(
                    "[%d/%d] account=%s: found %d items",
                    i + 1, len(accounts), account.name, len(items),
                )
            except Exception:
                logger.warning(
                    "Failed to collect news for account=%s (id=%d)",
                    account.name, account.account_id, exc_info=True,
                )

            if i < len(accounts) - 1:
                await asyncio.sleep(_INTER_REQUEST_DELAY)

        new_articles: list[CollectedArticleData] = []
        if new_items_map:
            items_list = list(new_items_map.values())
            titles = [item.title for item, _ in items_list]
            descriptions = [item.description for item, _ in items_list]

            title_embeddings = self._embedder.embed_passages(titles)
            summary_embeddings = self._embedder.embed_passages(descriptions)

            for idx, (item, account_ids) in enumerate(items_list):
                new_articles.append(
                    CollectedArticleData(
                        item=item,
                        title_embedding=title_embeddings[idx].tolist(),
                        summary_embedding=summary_embeddings[idx].tolist(),
                        account_ids=account_ids,
                    )
                )

        new_articles = deduplicate_articles(new_articles)

        if new_articles:
            db_embeddings = await self._fetch_recent_title_embeddings(db)
            new_articles = deduplicate_against_existing(
                new_articles, db_embeddings,
            )

        return NaverCollectResult(
            new_articles=new_articles,
            existing_links=existing_links_map,
        )

    _DB_DEDUP_DAYS = 7

    async def _get_existing_links(
        self, links: list[str], db: AsyncSession,
    ) -> set[str]:
        if not links:
            return set()
        result = await db.execute(
            text(
                "SELECT link FROM news_articles WHERE link = ANY(:links)"
            ),
            {"links": links},
        )
        return {row[0] for row in result.fetchall()}

    async def _fetch_recent_title_embeddings(
        self, db: AsyncSession,
    ) -> np.ndarray:
        result = await db.execute(
            text(
                "SELECT title_embedding::real[]"
                " FROM news_articles"
                " WHERE published_at >= CURRENT_DATE - :days * INTERVAL '1 day'"
                " AND title_embedding IS NOT NULL"
            ),
            {"days": self._DB_DEDUP_DAYS},
        )
        rows = result.fetchall()
        if not rows:
            return np.empty((0, 0), dtype=np.float32)
        return np.array([row[0] for row in rows], dtype=np.float32)