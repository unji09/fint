"""사용자 입력 필터 + 출력 검증. Prompt injection 방지용 3중 레이어 중 A-1."""

from __future__ import annotations

import re

MAX_INPUT_LENGTH = 2000

_BLOCKED_PATTERNS = [
    re.compile(r"ignore\s+(all\s+)?(previous|prior|above)\s+(instructions?|prompts?|rules?)", re.IGNORECASE),
    re.compile(r"이전\s*(지시사항|지시|명령|프롬프트|규칙).*무시", re.IGNORECASE),
    re.compile(r"(you\s+are|act\s+as|pretend\s+to\s+be)\s+(now\s+)?a\s+", re.IGNORECASE),
    re.compile(r"^\s*SYSTEM\s*:", re.IGNORECASE | re.MULTILINE),
    re.compile(r"^\s*\[SYSTEM\]", re.IGNORECASE | re.MULTILINE),
    re.compile(r"override\s+(all\s+)?(safety|security|restriction)", re.IGNORECASE),
    re.compile(r"forget\s+(all\s+)?(your\s+)?(instructions?|rules?|guidelines?)", re.IGNORECASE),
    re.compile(r"do\s+not\s+follow\s+(your\s+)?(instructions?|rules?|guidelines?)", re.IGNORECASE),
    re.compile(r"(jailbreak|prompt\s*inject)", re.IGNORECASE),
]


class GuardrailError(Exception):
    pass


def check_input(text: str) -> None:
    stripped = text.strip()

    if not stripped:
        raise GuardrailError("빈 입력입니다")

    if len(stripped) > MAX_INPUT_LENGTH:
        raise GuardrailError(f"입력이 너무 깁니다 (최대 {MAX_INPUT_LENGTH}자)")

    for pattern in _BLOCKED_PATTERNS:
        if pattern.search(stripped):
            raise GuardrailError("허용되지 않은 입력 패턴이 감지되었습니다")
