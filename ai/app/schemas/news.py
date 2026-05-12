"""뉴스 수집 API 스키마."""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel


class NewsCollectRequest(BaseModel):
    source: Literal["naver", "dart", "all"] = "naver"
    include_embeddings: bool = True


class CollectedArticle(BaseModel):
    title: str
    link: str | None = None
    original_link: str | None = None
    published_at: datetime
    content_summary: str | None = None
    title_embedding: list[float]
    summary_embedding: list[float]
    account_ids: list[int]


class ExistingArticleLink(BaseModel):
    link: str
    account_ids: list[int]


class NewsCollectResponse(BaseModel):
    total_accounts: int
    new_articles: list[CollectedArticle]
    existing_links: list[ExistingArticleLink]
    errors: list[str]