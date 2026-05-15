"""POST /api/v1/ai/next-actions — AI 전략 추천 엔드포인트."""
import logging

from fastapi import APIRouter, Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession

from app.clients import get_llm_client
from app.clients.llm import LLMClient
from app.core.db import get_db
from app.core.errors import BusinessException, CommonErrorCode
from app.core.security import get_tenant_id
from app.schemas.strategy import NextActionRequest, NextActionResponse, TriggerType
from app.strategy.context_builder import (
    build_context,
    load_pipeline_stages,
    map_category_to_stage_id,
)
from app.strategy.engine import recommend
from app.strategy.feature_extractor import extract_features, extract_features_dummy

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
        meeting_id=body.meeting_id,
        extra_context=body.context,
    )

    if not context_data.context_text.strip():
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            "전략 추천을 위한 근거 데이터가 없습니다",
        )

    # ===== 더미 모드 (테스트용, OPENAI_API_KEY 토큰 절약) =====
    features = extract_features_dummy(context_data.context_text)

    # ===== 실제 LLM 호출 모드 (운영용) =====
    # features = await extract_features(context_data.context_text, llm)

    if not features:
        raise BusinessException(
            CommonErrorCode.EXTERNAL_API_FAILED,
            "피처 추출에 실패했습니다",
        )

    recommendations = recommend(features, top_n=TOP_N, min_score=0.0)
    if not recommendations:
        raise BusinessException(
            CommonErrorCode.NOT_FOUND,
            "현재 상황에 적합한 추천 액션이 없습니다",
        )

    related_type = _resolve_related_type(body.trigger_type, body.meeting_id)
    sources = _build_sources(context_data)
    stages_map = await load_pipeline_stages(db, tenant_id)

    responses: list[NextActionResponse] = []
    for action, score, reason in recommendations:
        responses.append(
            NextActionResponse(
                action=action["name"],
                reason=reason,
                category=action.get("category", "GENERAL"),
                related_type=related_type,
                importance_score=round(min(score * 100, 100.0), 1),
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


def _resolve_related_type(trigger_type: TriggerType, meeting_id: int | None) -> str:
    if trigger_type == TriggerType.MEETING_CREATED or meeting_id is not None:
        return "MEETING"
    return "ACCOUNT"


def _derive_script(action: dict, features: dict) -> str:
    name = action.get("name", "")
    outcome = action.get("expected_outcome", "")
    persona = action.get("target_persona", [])
    persona_str = ", ".join(persona[:2]) if persona else "담당자"

    return (
        f"{persona_str}에게 연락하여 {name}을(를) 진행하세요. "
        f"기대 결과: {outcome}"
    )


def _build_sources(ctx) -> dict:
    news = [{"title": n.get("title", "")} for n in ctx.news_items]

    dart = [
        {"contentSummary": d.get("content_summary") or d.get("report_nm", "")}
        for d in ctx.dart_items
    ]

    crm: list[dict] = []
    if ctx.meeting:
        summary = ctx.meeting.get("summary") or ctx.meeting.get("memo") or ctx.meeting.get("title", "")
        crm.append({"summary": summary})

    return {"news": news, "dart": dart, "crm": crm}
