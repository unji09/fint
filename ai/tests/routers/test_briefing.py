"""POST /api/v1/ai/briefing 라우터 통합 테스트."""

from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.clients import get_llm_client
from app.clients.llm import LLMClient
from app.core.errors import BusinessException, CommonErrorCode, register_exception_handlers
from app.routers.briefing import router
from app.schemas.briefing import BriefingResponse

TENANT_ID = 1
_URL = "/api/v1/ai/briefing"
_HEADERS = {"X-Tenant-Id": str(TENANT_ID)}

SAMPLE_RESPONSE = BriefingResponse(
    key_points=[
        "지난 미팅에서 보안 인증 관련 추가 자료 요청 — 준비 여부 확인 필수",
        "가격 재협상 가능성 시사됨 — 대안 패키지 사전 준비 필요",
        "DART 공시상 매출 증가, IT 투자 확대 기조 — 예산 확보 가능성 높음",
    ],
    alerts=[
        "분위기 점수 72→55 하락 추세 — 가격 저항이 주 원인",
    ],
)

MINIMAL_REQUEST = {
    "activity_id": 789,
    "title": "2분기 계약 갱신 논의",
    "scheduled_at": "2026-05-16T14:00:00+09:00",
    "account_id": 123,
    "account_name": "삼성SDS",
}

FULL_REQUEST = {
    **MINIMAL_REQUEST,
    "industry": "IT서비스",
    "current_mood": "NEUTRAL",
    "mood_score": 55,
    "mood_reason": "지난 미팅에서 가격 관련 유보적 태도",
    "contacts": [
        {"name": "김부장", "position": "구매팀 부장", "personality": "데이터 중심 의사결정"},
        {"name": "이과장", "position": "기술팀 과장"},
    ],
    "deals": [
        {
            "deal_id": 456,
            "title": "클라우드 마이그레이션 2차",
            "current_stage": "NEGOTIATION",
            "probability": 60,
            "amount": 150000000,
        }
    ],
    "recent_meetings": [
        {
            "activity_id": 780,
            "title": "기술 검토 미팅",
            "date": "2026-05-08",
            "summary": "보안 인증 추가 자료 요청. 가격 재협상 가능성 시사.",
            "mood_score": 55,
            "mood_reason": "가격 저항은 있으나 기술적 관심 유지",
        }
    ],
    "signals": [
        {
            "signal_type": "DART",
            "title": "삼성SDS 1분기 실적 공시",
            "summary": "매출 전년 대비 12% 증가",
            "published_at": "2026-05-10",
            "importance_score": 0.85,
        }
    ],
    "wiki_summary": "2024년부터 거래 중. 보안 인증 필수 요구.",
}


@pytest.fixture
def mock_llm() -> MagicMock:
    llm = MagicMock(spec=LLMClient)
    llm.chat_structured = AsyncMock(return_value=SAMPLE_RESPONSE)
    return llm


@pytest.fixture
def test_app(mock_llm: MagicMock) -> FastAPI:
    app = FastAPI()
    register_exception_handlers(app)
    app.include_router(router)
    app.dependency_overrides = {
        get_llm_client: lambda: mock_llm,
    }
    return app


@pytest.fixture
async def ac(test_app: FastAPI) -> AsyncClient:
    async with AsyncClient(
        transport=ASGITransport(app=test_app),
        base_url="http://test",
    ) as client:
        yield client


# ── 정상 케이스 ─────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_briefing_full_request(ac: AsyncClient, mock_llm: MagicMock) -> None:
    """전체 필드가 채워진 요청 → 200 + key_points/alerts 반환."""
    resp = await ac.post(_URL, json=FULL_REQUEST, headers=_HEADERS)

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert len(data["key_points"]) == 3
    assert len(data["alerts"]) == 1
    mock_llm.chat_structured.assert_called_once()


@pytest.mark.asyncio
async def test_briefing_minimal_request(ac: AsyncClient, mock_llm: MagicMock) -> None:
    """필수 필드만 있는 최소 요청 → 200 정상 처리."""
    resp = await ac.post(_URL, json=MINIMAL_REQUEST, headers=_HEADERS)

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert "key_points" in data
    assert "alerts" in data
    mock_llm.chat_structured.assert_called_once()


@pytest.mark.asyncio
async def test_briefing_prompt_contains_account_name(ac: AsyncClient, mock_llm: MagicMock) -> None:
    """프롬프트에 고객사명이 포함되는지 검증."""
    await ac.post(_URL, json=FULL_REQUEST, headers=_HEADERS)

    call_args = mock_llm.chat_structured.call_args
    messages = call_args.kwargs.get("messages") or call_args[1].get("messages") or call_args[0][0]
    user_message = next(m["content"] for m in messages if m["role"] == "user")
    assert "삼성SDS" in user_message


@pytest.mark.asyncio
async def test_briefing_prompt_contains_deal_info(ac: AsyncClient, mock_llm: MagicMock) -> None:
    """프롬프트에 딜 정보가 포함되는지 검증."""
    await ac.post(_URL, json=FULL_REQUEST, headers=_HEADERS)

    call_args = mock_llm.chat_structured.call_args
    messages = call_args.kwargs.get("messages") or call_args[1].get("messages") or call_args[0][0]
    user_message = next(m["content"] for m in messages if m["role"] == "user")
    assert "클라우드 마이그레이션 2차" in user_message
    assert "NEGOTIATION" in user_message


@pytest.mark.asyncio
async def test_briefing_prompt_contains_signal(ac: AsyncClient, mock_llm: MagicMock) -> None:
    """프롬프트에 외부 시그널이 포함되는지 검증."""
    await ac.post(_URL, json=FULL_REQUEST, headers=_HEADERS)

    call_args = mock_llm.chat_structured.call_args
    messages = call_args.kwargs.get("messages") or call_args[1].get("messages") or call_args[0][0]
    user_message = next(m["content"] for m in messages if m["role"] == "user")
    assert "DART" in user_message
    assert "삼성SDS 1분기 실적 공시" in user_message


# ── 에러 케이스 ─────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_briefing_missing_required_field(ac: AsyncClient) -> None:
    """필수 필드 누락 → 400."""
    resp = await ac.post(_URL, json={"activity_id": 1}, headers=_HEADERS)
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_briefing_invalid_mood_score(ac: AsyncClient) -> None:
    """mood_score 범위 초과 → 400."""
    resp = await ac.post(_URL, json={**MINIMAL_REQUEST, "mood_score": 150}, headers=_HEADERS)
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_briefing_llm_failure(ac: AsyncClient, mock_llm: MagicMock) -> None:
    """LLM 호출 실패 → 502."""
    mock_llm.chat_structured = AsyncMock(
        side_effect=BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "LLM timeout")
    )
    resp = await ac.post(_URL, json=MINIMAL_REQUEST, headers=_HEADERS)
    assert resp.status_code == 502


@pytest.mark.asyncio
async def test_briefing_missing_tenant_header(ac: AsyncClient) -> None:
    """X-Tenant-Id 헤더 누락 → 422."""
    resp = await ac.post(_URL, json=MINIMAL_REQUEST)
    assert resp.status_code in (400, 422)
