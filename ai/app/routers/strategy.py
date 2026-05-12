"""POST /ai/next-actions — AI 전략 추천 엔드포인트."""
import logging
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Header

from app.clients import get_llm_client
from app.clients.llm import LLMClient
from app.core.errors import BusinessException, CommonErrorCode
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.schemas.strategy import NextActionRequest, NextActionResponse, SourceInfo
from app.strategy.engine import recommend
from app.strategy.feature_extractor import extract_features

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["strategy"])


@router.post("/next-actions")
async def generate_next_actions(
    body: NextActionRequest,
    tenant_id: int = Depends(get_tenant_id),
    llm: LLMClient = Depends(get_llm_client),
    x_tenant_id: str | None = Header(None, include_in_schema=True),
) -> ApiResponse[list[NextActionResponse]]:
    context = body.context or ""
    if not context.strip():
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            "context는 영업 상황 설명을 포함해야 합니다",
        )

    features = await extract_features(context, llm)
    if not features:
        raise BusinessException(
            CommonErrorCode.EXTERNAL_API_FAILED,
            "피처 추출에 실패했습니다",
        )

    recommendations = recommend(features, top_n=5, min_score=0.3)
    if not recommendations:
        raise BusinessException(
            CommonErrorCode.NOT_FOUND,
            "현재 상황에 적합한 추천 액션이 없습니다",
        )

    now = datetime.now(UTC)
    responses: list[NextActionResponse] = []
    for priority, (action, score, reason) in enumerate(recommendations, start=1):
        success_pct = min(int(score * 100), 99)

        risk = _derive_risk(action, features)
        script = _derive_script(action, features)
        sources = _derive_sources(action, features)

        responses.append(
            NextActionResponse(
                next_action_id=priority,
                account_id=body.account_id,
                action=action["name"],
                reason=reason,
                category=action.get("category", ""),
                priority=priority,
                success_probability=success_pct,
                sources=sources,
                recommended_script=script,
                risk=risk,
                created_at=now,
            )
        )

    logger.info(
        "next-actions generated: tenant_id=%d, account_id=%d, count=%d",
        tenant_id,
        body.account_id,
        len(responses),
    )
    return ApiResponse.ok(responses)


def _derive_risk(action: dict, features: dict) -> str:
    risks: list[str] = []
    stage = features.get("FEAT_007")

    if not features.get("FEAT_011") and stage in ("evaluation", "poc", "negotiation"):
        risks.append("챔피언 부재로 내부 추진력 약화 가능")
    if not features.get("FEAT_013") and stage in ("negotiation", "closing"):
        risks.append("Economic Buyer 미참여 상태에서 의사결정 지연 가능")
    if features.get("FEAT_012") == "weakening":
        risks.append("챔피언 영향력 약화 감지")

    days_in_stage = features.get("FEAT_008")
    if isinstance(days_in_stage, (int, float)) and days_in_stage >= 30:
        risks.append(f"현 단계 {int(days_in_stage)}일 경과 — 딜 모멘텀 저하 우려")

    objections = features.get("FEAT_059") or []
    if objections:
        risks.append(f"미해결 objection: {', '.join(objections[:2])}")

    if not risks:
        risks.append("특별한 리스크 요소 없음")

    return "; ".join(risks[:3])


def _derive_script(action: dict, features: dict) -> str:
    name = action.get("name", "")
    outcome = action.get("expected_outcome", "")
    persona = action.get("target_persona", [])
    persona_str = ", ".join(persona[:2]) if persona else "담당자"

    return (
        f"{persona_str}에게 연락하여 {name}을(를) 진행하세요. "
        f"기대 결과: {outcome}"
    )


def _derive_sources(action: dict, features: dict) -> SourceInfo:
    crm_sources: list[str] = []

    if features.get("FEAT_007"):
        crm_sources.append(f"현재 단계: {features['FEAT_007']}")
    if features.get("FEAT_049") and features["FEAT_049"] != "none":
        crm_sources.append(f"PoC 상태: {features['FEAT_049']}")
    if features.get("FEAT_059"):
        crm_sources.append(f"Objection: {', '.join(features['FEAT_059'][:2])}")

    methodologies = action.get("source_methodologies", [])
    if methodologies:
        crm_sources.append(f"방법론: {', '.join(methodologies[:2])}")

    return SourceInfo(news=[], dart=[], crm=crm_sources)
