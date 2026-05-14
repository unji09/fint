"""LLM-based feature extraction from natural language context."""
import json
import logging
import re

from app.clients.llm import LLMClient
from .data import FEATURES

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """당신은 IT 기술영업 상황 분석가입니다.
사용자가 영업 상황을 자연어로 설명하면, 그 내용을 사전 정의된 80개 지표(feature)값으로 추출해 JSON 형식으로 반환합니다.

규칙:
1. 명시적으로 언급되지 않은 지표는 null로 표시 (추측하지 마세요)
2. 모호한 표현은 가장 보수적인 값으로 매핑
3. 한국어/영어 혼용 입력도 허용하되, 추출 결과는 schema에 정의된 영문 enum 값으로
4. 모든 지표는 schema의 type/values를 정확히 따릅니다 (categorical은 enum 외 값 X)
5. boolean은 명시적 시그널 없으면 null (false 아님)
6. multi_select는 해당하는 값들의 배열, 없으면 빈 배열 []

반드시 순수 JSON만 반환하세요. 마크다운 코드블록(```)이나 설명 텍스트 없이 JSON 객체만 출력하세요."""


def _build_schema_reference() -> str:
    lines: list[str] = []
    for feat_id, feat in sorted(FEATURES.items()):
        desc = f"{feat_id} ({feat['name']}): type={feat['type']}"
        if "values" in feat:
            desc += f", values={feat['values']}"
        if "range" in feat:
            desc += f", range={feat['range']}"
        desc += f" — {feat.get('description', '')}"
        lines.append(desc)
    return "\n".join(lines)


_SCHEMA_REF = _build_schema_reference()

_JSON_BLOCK_RE = re.compile(r"```(?:json)?\s*([\s\S]*?)```")


def _extract_json(text: str) -> dict | None:
    block_match = _JSON_BLOCK_RE.search(text)
    if block_match:
        text = block_match.group(1).strip()

    text = text.strip()
    start = text.find("{")
    if start == -1:
        return None
    depth = 0
    end = start
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    try:
        return json.loads(text[start:end])
    except json.JSONDecodeError:
        return None


async def extract_features(context: str, llm: LLMClient) -> dict:
    """Extract 80 sales features from context text via LLM."""
    user_prompt = f"""다음 영업 상황을 80개 지표로 추출하세요. schema 정의를 정확히 따라야 합니다.

[영업 상황 설명]
{context}

[Schema 참조]
{_SCHEMA_REF}

[출력 형식]
반드시 아래 형식의 순수 JSON만 반환하세요:
{{"features": {{"FEAT_001": "enterprise", "FEAT_002": "finance", ...}}}}"""

    raw = await llm.chat(
        [{"role": "user", "content": f"{SYSTEM_PROMPT}\n\n{user_prompt}"}],
    )

    parsed = _extract_json(raw)
    if parsed is None:
        logger.error("Failed to parse LLM response as JSON: %s", raw[:200])
        return {}

    if "features" in parsed:
        return parsed["features"]
    return parsed


# ============================================================
# 테스트용 더미 피처 (OPENAI_API_KEY 토큰 절약)
# 실제 LLM 호출 → extract_features()
# 더미 모드   → extract_features_dummy()
# router에서 주석 전환으로 모드 변경
# ============================================================

_DUMMY_FEATURES: dict = {
    "FEAT_001": "enterprise",
    "FEAT_002": "technology",
    "FEAT_003": "korea",
    "FEAT_005": "existing",
    "FEAT_007": "evaluation",
    "FEAT_008": 15,
    "FEAT_009": "inbound",
    "FEAT_011": True,
    "FEAT_012": "stable",
    "FEAT_013": False,
    "FEAT_014": "not_engaged",
    "FEAT_015": "not_engaged",
    "FEAT_017": 3,
    "FEAT_019": "weekly",
    "FEAT_024": True,
    "FEAT_025": "quantified",
    "FEAT_029": True,
    "FEAT_033": "medium",
    "FEAT_049": None,
    "FEAT_059": [],
    "FEAT_070": None,
    "FEAT_080": False,
}


def extract_features_dummy(context: str) -> dict:
    """LLM 호출 없이 하드코딩된 더미 피처를 반환한다 (테스트용)."""
    logger.info("Using DUMMY features (LLM call skipped). context length=%d", len(context))
    return dict(_DUMMY_FEATURES)
