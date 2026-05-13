"""DART 전자공시 수집기.

DB에는 READ만 수행한다 (중복 체크용). 수집된 공시 데이터를 반환하면
Spring 서버가 DB에 INSERT를 담당한다.
"""
from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass, field

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.clients.dart import DartClient, DartDisclosureItem
from app.clients.embedder import EmbedderClient
from app.news.dart_classifier import analyze
from app.news.naver_collector import AccountInfo

logger = logging.getLogger(__name__)

_INTER_REQUEST_DELAY = 0.2
_SKIP_KEYWORDS = ("최대주주등소유주식변동신고서",)


@dataclass
class CollectedDisclosureData:
    item: DartDisclosureItem
    content: str | None
    content_summary: str | None
    title_embedding: list[float]
    summary_embedding: list[float]
    account_ids: list[int]


@dataclass
class DartCollectResult:
    new_disclosures: list[CollectedDisclosureData] = field(default_factory=list)
    existing_rcept_nos: dict[str, list[int]] = field(default_factory=dict)


class DartCollector:
    def __init__(
        self,
        dart_client: DartClient,
        embedder: EmbedderClient,
    ) -> None:
        self._dart = dart_client
        self._embedder = embedder

    async def collect_for_accounts(
        self,
        accounts: list[AccountInfo],
        db: AsyncSession,
    ) -> DartCollectResult:
        await self._dart.ensure_corp_codes()

        new_items_map: dict[str, tuple[DartDisclosureItem, list[int]]] = {}
        existing_rcept_map: dict[str, list[int]] = {}

        for i, account in enumerate(accounts):
            try:
                corp_info = self._dart.find_corp(account.name)
                if corp_info is None:
                    logger.debug("No DART corp_code for account=%s", account.name)
                    continue

                items = await self._dart.list_disclosures(corp_info.corp_code)
                if not items:
                    continue

                existing_rcepts = await self._get_existing_rcept_nos(
                    [item.rcept_no for item in items], db,
                )

                for item in items:
                    if any(kw in item.report_nm for kw in _SKIP_KEYWORDS):
                        continue
                    if item.rcept_no in existing_rcepts:
                        acct_list = existing_rcept_map.setdefault(item.rcept_no, [])
                        if account.account_id not in acct_list:
                            acct_list.append(account.account_id)
                    elif item.rcept_no in new_items_map:
                        if account.account_id not in new_items_map[item.rcept_no][1]:
                            new_items_map[item.rcept_no][1].append(account.account_id)
                    else:
                        new_items_map[item.rcept_no] = (item, [account.account_id])

                logger.info(
                    "[%d/%d] account=%s (corp=%s): found %d disclosures",
                    i + 1, len(accounts), account.name,
                    corp_info.corp_code, len(items),
                )
            except Exception:
                logger.warning(
                    "Failed to collect DART for account=%s (id=%d)",
                    account.name, account.account_id, exc_info=True,
                )

            if i < len(accounts) - 1:
                await asyncio.sleep(_INTER_REQUEST_DELAY)

        new_disclosures: list[CollectedDisclosureData] = []
        if new_items_map:
            items_list = list(new_items_map.values())

            contents: dict[str, str | None] = {}
            for rcept_no, (item, _) in new_items_map.items():
                content = await self._dart.fetch_document(rcept_no)
                contents[rcept_no] = content
                await asyncio.sleep(_INTER_REQUEST_DELAY)

            analyses: dict[str, DisclosureAnalysis] = {}
            titles = [item.report_nm for item, _ in items_list]
            summaries: list[str] = []
            for item, _ in items_list:
                content = contents.get(item.rcept_no)
                analysis = analyze(item.report_nm, content, flr_nm=item.flr_nm)
                analyses[item.rcept_no] = analysis
                summaries.append(analysis.signal_summary)

            title_embeddings = self._embedder.embed_passages(titles)
            summary_embeddings = self._embedder.embed_passages(summaries)

            for idx, (item, account_ids) in enumerate(items_list):
                a = analyses[item.rcept_no]
                new_disclosures.append(
                    CollectedDisclosureData(
                        item=item,
                        content=contents.get(item.rcept_no),
                        content_summary=a.signal_summary,
                        title_embedding=title_embeddings[idx].tolist(),
                        summary_embedding=summary_embeddings[idx].tolist(),
                        account_ids=account_ids,
                    )
                )

        return DartCollectResult(
            new_disclosures=new_disclosures,
            existing_rcept_nos=existing_rcept_map,
        )

    async def _get_existing_rcept_nos(
        self, rcept_nos: list[str], db: AsyncSession,
    ) -> set[str]:
        if not rcept_nos:
            return set()
        result = await db.execute(
            text(
                "SELECT rcept_no FROM dart_disclosures WHERE rcept_no = ANY(:rcept_nos)"
            ),
            {"rcept_nos": rcept_nos},
        )
        return {row[0] for row in result.fetchall()}
