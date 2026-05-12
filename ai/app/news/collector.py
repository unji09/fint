"""외부 데이터 수집 오케스트레이터 — 네이버 뉴스 / DART 공시.

DB에는 READ만 수행한다. 수집·임베딩 결과를 반환하면
Spring 서버가 DB에 INSERT를 담당한다.
"""
from __future__ import annotations

import logging

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.clients.embedder import EmbedderClient
from app.clients.naver import NaverNewsClient
from app.news.naver_collector import AccountInfo, NaverNewsCollector
from app.schemas.news import CollectedArticle, ExistingArticleLink, NewsCollectResponse

logger = logging.getLogger(__name__)


async def get_accounts_for_tenant(
    db: AsyncSession,
    tenant_id: int,
) -> list[AccountInfo]:
    result = await db.execute(
        text("""
            SELECT DISTINCT a.account_id, a.name
            FROM accounts a
            JOIN account_user_assignment aua ON a.account_id = aua.account_id
            JOIN users u ON aua.user_id = u.user_id
            WHERE u.tenant_id = :tenant_id
              AND a.is_deleted = false
        """),
        {"tenant_id": tenant_id},
    )
    return [
        AccountInfo(account_id=row[0], name=row[1])
        for row in result.fetchall()
    ]


async def collect_news(
    *,
    source: str,
    tenant_id: int,
    db: AsyncSession,
    naver_client: NaverNewsClient | None = None,
    embedder: EmbedderClient | None = None,
    include_embeddings: bool = True,
) -> NewsCollectResponse:
    accounts = await get_accounts_for_tenant(db, tenant_id)
    if not accounts:
        return NewsCollectResponse(
            total_accounts=0,
            new_articles=[],
            existing_links=[],
            errors=["해당 tenant에 등록된 고객사가 없습니다"],
        )

    errors: list[str] = []
    new_articles: list[CollectedArticle] = []
    existing_links: list[ExistingArticleLink] = []

    if source in ("naver", "all"):
        if naver_client is None or embedder is None:
            errors.append("네이버 뉴스 수집에 필요한 클라이언트가 설정되지 않았습니다")
        else:
            collector = NaverNewsCollector(naver_client, embedder)
            naver_result = await collector.collect_for_accounts(accounts, db)

            for article_data in naver_result.new_articles:
                new_articles.append(
                    CollectedArticle(
                        title=article_data.item.title,
                        link=article_data.item.link,
                        original_link=article_data.item.original_link or None,
                        published_at=article_data.item.pub_date,
                        content_summary=article_data.item.description or None,
                        title_embedding=article_data.title_embedding if include_embeddings else [],
                        summary_embedding=article_data.summary_embedding if include_embeddings else [],
                        account_ids=article_data.account_ids,
                    )
                )

            for link, account_ids in naver_result.existing_links.items():
                existing_links.append(
                    ExistingArticleLink(link=link, account_ids=account_ids)
                )

    if source in ("dart", "all"):
        errors.append("DART 수집은 추후 구현 예정입니다")

    logger.info(
        "collect_news done: tenant_id=%d, accounts=%d, new_articles=%d, existing_links=%d",
        tenant_id, len(accounts), len(new_articles), len(existing_links),
    )

    return NewsCollectResponse(
        total_accounts=len(accounts),
        new_articles=new_articles,
        existing_links=existing_links,
        errors=errors,
    )