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