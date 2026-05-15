"""DB에서 뉴스/DART/미팅/고객사 조회 후 LLM용 컨텍스트 문자열 조합."""
import logging
from dataclasses import dataclass, field

from sqlalchemy import text, bindparam
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import BusinessException, CommonErrorCode

logger = logging.getLogger(__name__)


@dataclass
class ContextData:
    account_name: str
    account_industry: str
    news_items: list[dict] = field(default_factory=list)
    dart_items: list[dict] = field(default_factory=list)
    meeting: dict | None = None
    context_text: str = ""


_ACCOUNT_SQL = text("""
    SELECT DISTINCT a.account_id, a.name, a.industry
    FROM accounts a
    JOIN account_user_assignment aua ON a.account_id = aua.account_id
    JOIN users u ON aua.user_id = u.user_id
    WHERE a.account_id = :account_id AND u.tenant_id = :tenant_id AND a.is_deleted = false
    LIMIT 1
""")

_NEWS_SQL = text(
    "SELECT n.news_article_id, n.title, n.content_summary, n.publisher, "
    "n.published_at::text, n.link "
    "FROM news_articles n "
    "WHERE n.news_article_id IN :ids"
).bindparams(bindparam("ids", expanding=True))

_DART_SQL = text(
    "SELECT d.dart_disclosure_id, d.report_nm, d.content_summary, "
    "d.corp_name, d.rcept_dt, d.rcept_no "
    "FROM dart_disclosures d "
    "WHERE d.dart_disclosure_id IN :ids"
).bindparams(bindparam("ids", expanding=True))

_MEETING_SQL = text("""
    SELECT a.activity_id, a.title, a.memo, a.summary::text AS summary,
           a.start_at::text AS start_at, a.end_at::text AS end_at, a.type
    FROM activities a
    JOIN users u ON a.user_id = u.user_id
    WHERE a.activity_id = :meeting_id AND u.tenant_id = :tenant_id
    LIMIT 1
""")

_PIPELINE_STAGES_SQL = text("""
    SELECT ps.pipeline_stage_id, ps.name, ps.sort_order
    FROM pipeline_stages ps
    WHERE ps.tenant_id = :tenant_id
    ORDER BY ps.sort_order
""")

_CATEGORY_TO_SORT_ORDER: dict[str, int] = {
    "Prospecting": 1,
    "Discovery": 1,
    "Qualification": 2,
    "Demo & Technical Evaluation": 3,
    "POC Management": 3,
    "Champion Building & Multi-thread": 2,
    "Stakeholder-Specific (CIO/CISO/IT/Procurement/Finance)": 3,
    "Objection Handling & Risk Mitigation": 4,
    "Pricing & Negotiation": 5,
    "Closing & MAP": 6,
    "Implementation & Adoption": 7,
    "Expansion & Renewal": 7,
    "Korean Market Specific": 2,
}


async def build_context(
    db: AsyncSession,
    *,
    tenant_id: int,
    account_id: int,
    news_article_ids: list[int] | None = None,
    dart_disclosure_ids: list[int] | None = None,
    meeting_id: int | None = None,
    extra_context: str | None = None,
) -> ContextData:
    result = await db.execute(_ACCOUNT_SQL, {"account_id": account_id, "tenant_id": tenant_id})
    account_row = result.mappings().first()
    if not account_row:
        raise BusinessException(CommonErrorCode.NOT_FOUND, "해당 고객사를 찾을 수 없습니다")

    account_name = account_row["name"]
    account_industry = account_row["industry"]

    news_items: list[dict] = []
    if news_article_ids:
        result = await db.execute(_NEWS_SQL, {"ids": news_article_ids})
        news_items = [dict(row) for row in result.mappings().all()]

    dart_items: list[dict] = []
    if dart_disclosure_ids:
        result = await db.execute(_DART_SQL, {"ids": dart_disclosure_ids})
        dart_items = [dict(row) for row in result.mappings().all()]

    meeting: dict | None = None
    if meeting_id:
        result = await db.execute(_MEETING_SQL, {"meeting_id": meeting_id, "tenant_id": tenant_id})
        row = result.mappings().first()
        if row:
            meeting = dict(row)

    parts: list[str] = []
    parts.append(f"[고객사 정보]\n회사명: {account_name}\n업종: {account_industry}")

    if news_items:
        lines = ["[관련 뉴스]"]
        for n in news_items:
            title = n.get("title", "")
            summary = n.get("content_summary") or ""
            lines.append(f"- {title}: {summary}" if summary else f"- {title}")
        parts.append("\n".join(lines))

    if dart_items:
        lines = ["[DART 공시]"]
        for d in dart_items:
            report = d.get("report_nm", "")
            summary = d.get("content_summary") or ""
            lines.append(f"- {report}: {summary}" if summary else f"- {report}")
        parts.append("\n".join(lines))

    if meeting:
        lines = ["[미팅 정보]", f"제목: {meeting.get('title', '')}"]
        if meeting.get("memo"):
            lines.append(f"메모: {meeting['memo']}")
        if meeting.get("summary"):
            lines.append(f"요약: {meeting['summary']}")
        parts.append("\n".join(lines))

    if extra_context:
        parts.append(f"[추가 컨텍스트]\n{extra_context}")

    return ContextData(
        account_name=account_name,
        account_industry=account_industry,
        news_items=news_items,
        dart_items=dart_items,
        meeting=meeting,
        context_text="\n\n".join(parts),
    )


async def load_pipeline_stages(db: AsyncSession, tenant_id: int) -> dict[int, int]:
    """tenant의 {sort_order: pipeline_stage_id} 매핑을 1회 조회."""
    result = await db.execute(_PIPELINE_STAGES_SQL, {"tenant_id": tenant_id})
    stages = result.mappings().all()
    if not stages:
        return {}
    return {row["sort_order"]: row["pipeline_stage_id"] for row in stages}


def map_category_to_stage_id(category: str, stages_map: dict[int, int]) -> int:
    """액션 카테고리 → pipeline_stage_id 변환 (사전 로딩된 stages_map 사용)."""
    if not stages_map:
        return 1
    target_order = _CATEGORY_TO_SORT_ORDER.get(category)
    if target_order and target_order in stages_map:
        return stages_map[target_order]
    return min(stages_map.values())
