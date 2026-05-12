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
from app.schemas.news import (
    CollectedNewsArticle,
    DartResult,
    ExistingNewsLink,
    NewsResult,
    SignalsCollectResponse,
)

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
) -> SignalsCollectResponse:
    accounts = await get_accounts_for_tenant(db, tenant_id)
    if not accounts:
        return SignalsCollectResponse(
            total_accounts=0,
            news=NewsResult(new_articles=[], existing_links=[]),
            dart=DartResult(new_disclosures=[], existing_rcept_nos=[]),
            errors=["해당 tenant에 등록된 고객사가 없습니다"],
        )

    errors: list[str] = []
    news_result = NewsResult(new_articles=[], existing_links=[])
    dart_result = DartResult(new_disclosures=[], existing_rcept_nos=[])

    if source in ("naver", "all"):
        if naver_client is None or embedder is None:
            errors.append("네이버 뉴스 수집에 필요한 클라이언트가 설정되지 않았습니다")
        else:
            collector = NaverNewsCollector(naver_client, embedder)
            naver_data = await collector.collect_for_accounts(accounts, db)

            new_articles = []
            for ad in naver_data.new_articles:
                new_articles.append(
                    CollectedNewsArticle(
                        title=ad.item.title,
                        link=ad.item.link,
                        original_link=ad.item.original_link or None,
                        published_at=ad.item.pub_date,
                        content_summary=ad.item.description or None,
                        title_embedding=ad.title_embedding if include_embeddings else [],
                        summary_embedding=ad.summary_embedding if include_embeddings else [],
                        account_ids=ad.account_ids,
                    )
                )

            existing_links = [
                ExistingNewsLink(link=link, account_ids=acct_ids)
                for link, acct_ids in naver_data.existing_links.items()
            ]

            news_result = NewsResult(
                new_articles=new_articles,
                existing_links=existing_links,
            )

    if source in ("dart", "all"):
        errors.append("DART 수집은 추후 구현 예정입니다")

    logger.info(
        "collect done: tenant=%d, accounts=%d, news_new=%d, news_existing=%d",
        tenant_id, len(accounts),
        len(news_result.new_articles), len(news_result.existing_links),
    )

    return SignalsCollectResponse(
        total_accounts=len(accounts),
        news=news_result,
        dart=dart_result,
        errors=errors,
    )