import logging

from fastapi import APIRouter, Depends, Header

from app.clients import get_llm_client
from app.clients.llm import LLMClient
from app.core.response import ApiResponse
from app.schemas.briefing import BriefingRequest, BriefingResponse

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/ai", tags=["Briefing"])

SYSTEM_PROMPT = """\
당신은 B2B 영업 전문가입니다.
미팅에 들어가기 전 영업 담당자가 반드시 확인해야 할 브리핑을 작성하세요.

[어조 규칙]
- 명령형("~하라", "~할 것") 금지.
- "~가 필요합니다", "~을 확인해야 합니다", "~을 제안해보세요", "~이 예상됩니다" 등 정중한 권유/안내체를 사용할 것.

[출력 규칙]
- key_points: 미팅 전 반드시 확인할 핵심 포인트 3~5개. 각 포인트는 한 문장으로, 구체적 수치/이름/사실을 포함할 것.
- alerts: 아래 해당 사항이 있을 때만 1~3개 작성. 해당 사항이 없으면 빈 배열.

[key_points 우선순위 — 반드시 이 순서대로 배치]
1순위: 딜 현황 (현재 단계, 성사 확률, 금액 등 딜 진행 상황)
2순위: 뉴스/보도 (고객사 관련 최신 뉴스에서 추출한 영업 기회/위험)
3순위: 고객 분위기 (이전 미팅 기준 분위기 추이 — "현재 분위기"가 아닌 "이전 미팅 분위기 점수는 N점"으로 표현)
4순위: DART 공시 (재무/공시 정보에서 추출한 시사점)
5순위: 참석자 성향/이전 미팅 후속 조치
- 해당 데이터가 없는 순위는 건너뛰고 다음 순위로 이동할 것.

[key_points 표현 규칙]
- 분위기 점수를 언급할 때: "현재 분위기 점수는 N점" 대신 "이전 미팅 분위기 점수는 N점으로" 형태를 사용.
- 딜 정보를 언급할 때: 딜 이름, 현재 단계, 확률을 구체적으로 포함할 것.

[alerts 판단 기준 — 아래 중 하나라도 해당되면 작성]
- 분위기 점수가 이전 미팅 대비 하락한 경우 (예: 72→55)
- 딜 성사 확률이 50% 미만이거나 하락 추세인 경우
- 최근 미팅에서 거절/유보/부정적 반응이 감지된 경우
- 외부 시그널에 부정적 뉴스가 포함된 경우 (소송, 실적 악화, 구조조정, 경쟁사 전환 등)
- 마지막 미팅으로부터 장기간(30일 이상) 경과한 경우
- 핵심 의사결정자가 변경/퇴사한 정보가 있는 경우"""


def _build_user_prompt(req: BriefingRequest) -> str:
    sections: list[str] = []

    sections.append(f"## 미팅 정보\n- 제목: {req.title}\n- 예정 시각: {req.scheduled_at}")

    account_lines = [f"- 고객사: {req.account_name}"]
    if req.industry:
        account_lines.append(f"- 업종: {req.industry}")
    if req.current_mood:
        account_lines.append(f"- 현재 분위기: {req.current_mood}")
    if req.mood_score is not None:
        account_lines.append(f"- 분위기 점수: {req.mood_score}/100")
    if req.mood_reason:
        account_lines.append(f"- 분위기 사유: {req.mood_reason}")
    sections.append("## 고객 프로필\n" + "\n".join(account_lines))

    if req.contacts:
        lines = []
        for c in req.contacts:
            parts = [c.name]
            if c.position:
                parts.append(c.position)
            if c.personality:
                parts.append(f"성향: {c.personality}")
            lines.append("- " + " / ".join(parts))
        sections.append("## 미팅 참석자\n" + "\n".join(lines))

    if req.deals:
        lines = []
        for d in req.deals:
            parts = [f"{d.title} ({d.current_stage})"]
            if d.probability is not None:
                parts.append(f"확률 {d.probability}%")
            if d.amount is not None:
                parts.append(f"금액 {d.amount:,}원")
            lines.append("- " + ", ".join(parts))
        sections.append("## 진행 중 딜\n" + "\n".join(lines))

    if req.recent_meetings:
        lines = []
        for m in req.recent_meetings:
            parts = [f"{m.title} ({m.date})"]
            if m.summary:
                parts.append(m.summary)
            if m.mood_score is not None:
                parts.append(f"분위기 {m.mood_score}/100")
            if m.mood_reason:
                parts.append(m.mood_reason)
            lines.append("- " + " | ".join(parts))
        sections.append("## 최근 미팅 히스토리\n" + "\n".join(lines))

    if req.signals:
        lines = []
        for s in req.signals:
            parts = [f"[{s.signal_type}] {s.title} ({s.published_at})"]
            if s.summary:
                parts.append(s.summary)
            if s.importance_score is not None:
                parts.append(f"중요도 {s.importance_score:.2f}")
            lines.append("- " + " | ".join(parts))
        sections.append("## 외부 시그널\n" + "\n".join(lines))

    if req.wiki_summary:
        sections.append(f"## 고객사 위키 요약\n{req.wiki_summary}")

    return "\n\n".join(sections)


@router.post("/briefing")
async def generate_briefing(
    body: BriefingRequest,
    x_tenant_id: int = Header(..., alias="X-Tenant-Id"),
    llm: LLMClient = Depends(get_llm_client),
) -> ApiResponse[BriefingResponse]:
    tenant_id = x_tenant_id
    logger.info(
        "[Briefing] 요청 tenant=%d activityId=%d account=%s",
        tenant_id, body.activity_id, body.account_name,
    )

    user_prompt = _build_user_prompt(body)

    result: BriefingResponse = await llm.chat_structured(
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        response_model=BriefingResponse,
    )

    logger.info(
        "[Briefing] 완료 activityId=%d keyPoints=%d alerts=%d",
        body.activity_id, len(result.key_points), len(result.alerts),
    )

    return ApiResponse.ok(result)
