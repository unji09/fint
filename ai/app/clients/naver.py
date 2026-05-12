"""네이버 뉴스 검색 API 클라이언트."""
from __future__ import annotations

import html
import logging
import re
from dataclasses import dataclass
from datetime import datetime
from email.utils import parsedate_to_datetime

import httpx

logger = logging.getLogger(__name__)

_TAG_RE = re.compile(r"<[^>]+>")
_API_URL = "https://openapi.naver.com/v1/search/news.json"


@dataclass(frozen=True, slots=True)
class NaverNewsItem:
    title: str
    original_link: str
    link: str
    description: str
    pub_date: datetime


def _strip_html(text: str) -> str:
    return html.unescape(_TAG_RE.sub("", text)).strip()


def _parse_pub_date(raw: str) -> datetime:
    return parsedate_to_datetime(raw)


class NaverNewsClient:
    def __init__(self, client_id: str, client_secret: str) -> None:
        self._client_id = client_id
        self._client_secret = client_secret

    async def search(
        self,
        query: str,
        *,
        display: int = 100,
        sort: str = "date",
    ) -> list[NaverNewsItem]:
        headers = {
            "X-Naver-Client-Id": self._client_id,
            "X-Naver-Client-Secret": self._client_secret,
        }
        params = {
            "query": query,
            "display": display,
            "sort": sort,
        }

        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(_API_URL, headers=headers, params=params)
            resp.raise_for_status()

        data = resp.json()
        items: list[NaverNewsItem] = []
        for raw in data.get("items", []):
            try:
                items.append(
                    NaverNewsItem(
                        title=_strip_html(raw["title"]),
                        original_link=raw.get("originallink", ""),
                        link=raw["link"],
                        description=_strip_html(raw.get("description", "")),
                        pub_date=_parse_pub_date(raw["pubDate"]),
                    )
                )
            except Exception:
                logger.warning("Failed to parse news item: %s", raw, exc_info=True)

        return items