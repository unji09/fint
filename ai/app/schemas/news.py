"""외부 데이터(뉴스/DART) 수집 API 스키마.

응답 필드명은 DB 테이블 컬럼과 1:1 매핑된다.
Spring은 변환 없이 바로 INSERT할 수 있다.
"""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel


class SignalsCollectRequest(BaseModel):
    source: Literal["naver", "dart", "all"] = "naver"
    include_embeddings: bool = True
    account_ids: list[int] | None = None


# ── news_articles 테이블 매핑 ──

class CollectedNewsArticle(BaseModel):
    title: str
    link: str | None = None
    original_link: str | None = None
    published_at: datetime
    content_summary: str | None = None
    title_embedding: list[float]
    summary_embedding: list[float]
    account_ids: list[int]


class ExistingNewsLink(BaseModel):
    link: str
    account_ids: list[int]


class NewsResult(BaseModel):
    new_articles: list[CollectedNewsArticle]
    existing_links: list[ExistingNewsLink]


# ── dart_disclosures 테이블 매핑 ──

class CollectedDartDisclosure(BaseModel):
    corp_code: str
    corp_name: str
    stock_code: str | None = None
    corp_cls: str | None = None
    report_nm: str
    rcept_no: str
    flr_nm: str | None = None
    rcept_dt: str
    rm: str | None = None
    content: str | None = None
    content_summary: str | None = None
    title_embedding: list[float]
    summary_embedding: list[float]
    account_ids: list[int]


class ExistingDartRceptNo(BaseModel):
    rcept_no: str
    account_ids: list[int]


class DartResult(BaseModel):
    new_disclosures: list[CollectedDartDisclosure]
    existing_rcept_nos: list[ExistingDartRceptNo]


# ── 통합 응답 ──

class SignalsCollectResponse(BaseModel):
    total_accounts: int
    news: NewsResult
    dart: DartResult
    errors: list[str]