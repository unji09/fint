import logging

from fastapi import APIRouter, BackgroundTasks, Depends

from app.clients import get_llm_client
from app.clients.llm import LLMClient
from app.clients.spring import SpringClient
from app.core.config import Settings, get_settings
from app.core.errors import BusinessException
from app.core.response import ApiResponse
from app.schemas.mood import MoodAnalysisRequest, MoodAnalysisResult

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/mood", tags=["Mood"])

MOOD_PROMPT = """당신은 B2B 영업 전문가입니다.
아래는 미팅에서 고객이 발화한 내용입니다.
고객 발화를 기반으로 영업 담당자와 고객 간의 관계 분위기를 분석하세요.

응답은 반드시 한국어로 작성합니다. 전사본 언어와 무관하게 모든 출력 텍스트는 한국어로 통일합니다.

[판단 기준]
- 80~100: 강한 신뢰, 명확한 긍정 합의, 다음 액션 확정
- 60~79 : 우호적 분위기, 관심 표현, 추가 논의 의지
- 40~59 : 입장 불명확, 유보적 반응, 추가 검토 필요
- 20~39 : 부정적 반응, 가격 저항, 관심 저하
- 0~19  : 강한 거절, 갈등, 관계 위기

[출력 항목]
- mood_score: 0~100 숫자
- reason: 점수 이유 2~3문장
- key_signals: 주요 근거 발언 3개 이내
- key_discussion: 핵심 논의 내용 한 문장
- customer_needs: 고객 니즈 한 문장
- agreements: 합의 사항 한 문장
- action_items: 다음 액션 리스트

[출력 언어]
- 모든 텍스트 필드(reason, key_signals, key_discussion, customer_needs, agreements, action_items)는 한국어로 작성합니다.
- 영어 단어를 그대로 옮기지 말고 자연스러운 한국어로 풀어 씁니다(고유명사·제품명 제외).

[작성 규칙 — 환각 방지]
- 전사본에 명시되지 않은 내용은 절대 추가하지 않습니다.
- 추론이 필요한 경우에도 반드시 전사본 발언을 근거로 합니다.
- 근거 발언이 없는 항목은 비어 있는 값(빈 문자열 또는 빈 리스트)으로 둡니다.

[단일 문장 항목 작성 규칙 — key_discussion / customer_needs / agreements]
- 한 문장으로 작성하되, 여러 주제가 있으면 우선순위가 높은 것 위주로 압축하고 부수 주제는 쉼표로 이어 붙입니다.
- "구체화가 필요하다", "추가 논의가 필요하다"와 같은 추상적 슬로건은 금지합니다. 무엇을 어떻게 다뤘는지 구체적으로 명시합니다.

[action_items 작성 규칙]
- 각 항목은 "무엇을 한다" 형태로 명시합니다.
- 주체(영업 담당자 / 고객)와 기한이 전사본에 등장하면 포함합니다.
- "구체화한다", "추가 논의한다", "검토한다"와 같은 메타 액션은 제외하고, 실행 가능한 단위로 작성합니다.

[전사본]
{transcript}"""


def get_spring_client(settings: Settings = Depends(get_settings)) -> SpringClient:
    return SpringClient(
        base_url=settings.SPRING_BASE_URL,
        internal_secret=settings.INTERNAL_SECRET,
    )


async def _analyze_and_callback(
    request: MoodAnalysisRequest,
    llm: LLMClient,
    spring: SpringClient,
) -> None:
    try:
        result: MoodAnalysisResult = await llm.chat_structured(
            messages=[
                {
                    "role": "user",
                    "content": MOOD_PROMPT.format(transcript=request.transcript),
                }
            ],
            response_model=MoodAnalysisResult,
        )

        await spring.send_mood_callback(
            activity_id=request.activity_id,
            account_id=request.account_id,
            tenant_id=request.tenant_id,
            mood_score=result.mood_score,
            reason=result.reason,
            key_signals=result.key_signals,
            summary={
                "keyDiscussion": result.key_discussion,
                "customerNeeds": result.customer_needs,
                "agreements": result.agreements,
                "actionItems": result.action_items,
            }
        )

        logger.info(
            "[MoodAnalysis] 완료 activityId=%d score=%d",
            request.activity_id,
            result.mood_score,
        )

    except BusinessException as e:
        logger.error(
            "[MoodAnalysis] 실패 activityId=%d error=%s",
            request.activity_id,
            e.detail,
        )
    except Exception as e:
        logger.exception(
            "[MoodAnalysis] 예외 activityId=%d error=%s",
            request.activity_id,
            str(e),
        )


@router.post("/analyze")
async def analyze_mood(
    body: MoodAnalysisRequest,
    background_tasks: BackgroundTasks,
    llm: LLMClient = Depends(get_llm_client),
    spring: SpringClient = Depends(get_spring_client),
) -> ApiResponse[dict]:
    background_tasks.add_task(_analyze_and_callback, body, llm, spring)
    return ApiResponse.ok({"activityId": body.activity_id, "status": "PROCESSING"})