"""Function Calling tool 정의 — 의도 파악용 3개 tool."""

from __future__ import annotations

import json
from typing import Any

from app.schemas.dashboard import QuerySpec, SemanticSearchSpec

TOOL_QUERY_STRUCTURED: dict[str, Any] = {
    "type": "function",
    "function": {
        "name": "query_structured_data",
        "description": (
            "CRM 정형 데이터베이스를 조회합니다. "
            "고객사(accounts), 딜(deals), 활동(activities), 매출 등 정형 데이터를 조회할 때 사용하세요."
        ),
        "parameters": QuerySpec.model_json_schema(),
    },
}

TOOL_SEARCH_NEWS: dict[str, Any] = {
    "type": "function",
    "function": {
        "name": "search_news",
        "description": (
            "뉴스/DART 공시를 유사도 기반으로 검색합니다. "
            "기업 관련 뉴스, 공시, 외부 정보를 검색할 때 사용하세요."
        ),
        "parameters": SemanticSearchSpec.model_json_schema(),
    },
}

TOOL_REJECT: dict[str, Any] = {
    "type": "function",
    "function": {
        "name": "reject_query",
        "description": (
            "CRM과 무관한 질의를 거부합니다. "
            "날씨, 코딩, 일반 상식 등 B2B 영업 CRM과 관련 없는 질의일 때 사용하세요."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "reason": {
                    "type": "string",
                    "description": "거부 사유를 사용자에게 안내하는 한국어 메시지",
                },
            },
            "required": ["reason"],
        },
    },
}

ALL_TOOLS: list[dict[str, Any]] = [TOOL_QUERY_STRUCTURED, TOOL_SEARCH_NEWS, TOOL_REJECT]


def parse_tool_calls(message: Any) -> list[dict[str, Any]]:
    """OpenAI ChatCompletionMessage → tool call 리스트."""
    if not hasattr(message, "tool_calls") or not message.tool_calls:
        return []
    return [
        {
            "id": tc.id,
            "name": tc.function.name,
            "arguments": json.loads(tc.function.arguments),
        }
        for tc in message.tool_calls
    ]
