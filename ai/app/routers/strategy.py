"""POST /api/v1/ai/next-actions — AI 전략 추천 엔드포인트."""
import logging

from fastapi import APIRouter, Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession

from app.clients import get_llm_client
from app.clients.llm import LLMClient
from app.core.db import get_db
from app.core.errors import BusinessException, CommonErrorCode
from app.core.security import get_tenant_id
from app.schemas.strategy import (
    FeedbackRequest,
    NextActionRequest,
    NextActionResponse,
)
from app.strategy.context_builder import (
    build_context,
    load_pipeline_stages,
    map_category_to_stage_id,
)
from app.strategy.engine import recommend
from app.strategy.feature_extractor import (
    extract_features,
    extract_features_dummy,
    extract_features_mock,
)
from app.strategy.gap_check import check_gaps
from app.strategy.urgency import evaluate_urgency

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/ai", tags=["strategy"])

TOP_N = 4


@router.post("/next-actions")
async def generate_next_actions(
    body: NextActionRequest,
    tenant_id: int = Depends(get_tenant_id),
    db: AsyncSession = Depends(get_db),
    llm: LLMClient = Depends(get_llm_client),
    x_tenant_id: str | None = Header(None, include_in_schema=True),
) -> list[NextActionResponse]:
    context_data = await build_context(
        db,
        tenant_id=tenant_id,
        account_id=body.account_id,
        news_article_ids=body.news_article_ids,
        dart_disclosure_ids=body.dart_disclosure_ids,
        meeting_ids=body.meeting_ids,
        extra_context=body.context,
    )

    if not context_data.context_text.strip():
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            "전략 추천을 위한 근거 데이터가 없습니다",
        )

    # ===== 더미 모드 (테스트용, OPENAI_API_KEY 토큰 절약) =====
    # features = extract_features_dummy(context_data.context_text)

    # ===== Mock 모드 (API 호출 없이 regex 기반 추출) =====
    # features = extract_features_mock(context_data.context_text)

    # ===== 실제 LLM 호출 모드 (운영용, OpenAI function calling) =====
    features = await extract_features(context_data.context_text, llm)

    if not features:
        raise BusinessException(
            CommonErrorCode.EXTERNAL_API_FAILED,
            "피처 추출에 실패했습니다",
        )

    gaps = check_gaps(features)
    if not gaps["ok_to_recommend"]:
        logger.warning(
            "Hard-required features missing: %s (proceeding with degraded quality)",
            gaps["missing_hard"],
        )

    recommendations = recommend(features, top_n=TOP_N, min_score=0.0)
    if not recommendations:
        raise BusinessException(
            CommonErrorCode.NOT_FOUND,
            "현재 상황에 적합한 추천 액션이 없습니다",
        )

    related_type = _resolve_related_type(body.trigger_type, body.meeting_ids)
    sources = _build_sources(context_data)
    stages_map = await load_pipeline_stages(db, tenant_id)

    responses: list[NextActionResponse] = []
    for action, score, reason in recommendations:
        urgency_data = evaluate_urgency(features, action)
        responses.append(
            NextActionResponse(
                action=action["name"],
                reason=reason,
                category=action.get("category", "GENERAL"),
                related_type=related_type,
                importance_score=urgency_data["n_stars"],
                success_probability=min(int(score * 100), 99),
                sources=sources,
                recommended_script=_derive_script(action, features),
                pipeline_stage_id=map_category_to_stage_id(
                    action.get("category", ""), stages_map,
                ),
            )
        )

    logger.info(
        "next-actions generated: tenant_id=%d, account_id=%d, trigger=%s, count=%d",
        tenant_id, body.account_id, body.trigger_type.value, len(responses),
    )
    return responses


@router.post("/next-actions/feedback")
async def submit_feedback(
    body: FeedbackRequest,
    tenant_id: int = Depends(get_tenant_id),
    x_tenant_id: str | None = Header(None, include_in_schema=True),
) -> dict:
    from app.strategy.feedback import save_feedback

    save_feedback(
        features={},
        recommendation={"id": body.action_id, "name": body.action_name},
        label=body.label,
        label_reason=body.label_reason,
        session_id=body.session_id,
    )
    return {"status": "ok"}


def _resolve_related_type(trigger_type, meeting_ids: list[int] | None) -> str:
    from app.schemas.strategy import TriggerType
    if trigger_type == TriggerType.MEETING_CREATED or (meeting_ids and len(meeting_ids) > 0):
        return "MEETING"
    return "ACCOUNT"


def _derive_script(action: dict, features: dict) -> str:
    name = action.get("name", "")
    outcome = action.get("expected_outcome", "")
    persona = action.get("target_persona", [])
    persona_str = ", ".join(persona[:2]) if persona else "담당자"

    return (
        f"{persona_str}에게 연락하여 {name}을(를) 진행하세요.\n"
        f"기대 결과: {outcome}"
    )


_DART_BASE_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo="


def _build_sources(ctx) -> dict:
    news = []
    for n in ctx.news_items:
        item: dict[str, str] = {
            "title": n.get("title", ""),
            "summary": n.get("content_summary") or "",
        }
        link = n.get("link")
        if link:
            item["url"] = link
        news.append(item)

    dart = []
    for d in ctx.dart_items:
        item: dict[str, str] = {
            "title": d.get("report_nm", ""),
            "summary": d.get("content_summary") or "",
        }
        rcept_no = d.get("rcept_no")
        if rcept_no:
            item["url"] = f"{_DART_BASE_URL}{rcept_no}"
        dart.append(item)

    crm: list[dict] = []
    for m in ctx.meetings:
        crm.append({
            "title": m.get("title", ""),
            "summary": m.get("summary") or m.get("memo") or "",
        })

    return {"news": news, "dart": dart, "crm": crm}
