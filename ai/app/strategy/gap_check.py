"""Features dict가 추천에 충분한지 검사 + 부족하면 follow-up 질문 생성."""
from __future__ import annotations

from .data import FEATURES

HARD_REQUIRED = ["FEAT_007"]

SOFT_RECOMMENDED = ["FEAT_002", "FEAT_003", "FEAT_001"]

STAGE_CONDITIONAL_REQUIRED: dict[str, list[str]] = {
    "poc":         ["FEAT_049"],
    "negotiation": ["FEAT_059"],
    "renewal":     ["FEAT_073"],
    "closing":     ["FEAT_065"],
    "expansion":   ["FEAT_071", "FEAT_074"],
    "evaluation":  ["FEAT_011"],
    "discovery":   ["FEAT_032"],
}

QUESTIONS: dict[str, str] = {
    "FEAT_007": "지금 영업 단계? (디스커버리 / 자격검증 / 데모 / 평가 / PoC / 협상 / 클로징 / 온보딩 / 갱신 / 확장)",
    "FEAT_002": "고객 산업? (금융 / 의료 / 제조 / 공공 / 리테일 / tech_saas / 통신 / 에너지 / 교육 / 기타)",
    "FEAT_003": "고객 위치? (한국 / 미국 / 유럽 / APAC / 글로벌)",
    "FEAT_001": "회사 규모? (SMB / mid-market / 엔터프라이즈)",
    "FEAT_011": "주 챔피언 식별됐어? (true / false)",
    "FEAT_049": "PoC 단계? (스코핑 / 합의서 / 진행중 / 완료-통과 / 완료-실패 / 연장 / 취소)",
    "FEAT_059": "현재 표면화된 objection? (price / no_budget / wrong_timing / 등)",
    "FEAT_073": "갱신까지 며칠? (숫자, 음수면 만료)",
    "FEAT_065": "Mutual Action Plan 작성됐어? (true / false)",
    "FEAT_071": "사용량 vs plan limit %? (숫자, 0-200)",
    "FEAT_074": "확장 시그널? (multi_team_usage / limit_approaching / new_use_case_mentioned / 등)",
    "FEAT_032": "구체 페인 식별됐어? (true / false)",
}


def check_gaps(features: dict) -> dict:
    """Returns:
        ok_to_recommend: bool — 추천 진행 가능 여부
        missing_hard:    필수인데 없는 feature_id 목록
        missing_conditional: 현재 stage에서 추가로 필요한데 없는 feature
        missing_soft:    있으면 정확도 향상인데 없는 feature
        questions:       missing 각각의 한국어 질문 dict
    """
    set_ids = {fid for fid, v in features.items() if v is not None}
    missing_hard = [fid for fid in HARD_REQUIRED if fid not in set_ids]
    missing_soft = [fid for fid in SOFT_RECOMMENDED if fid not in set_ids]

    stage = features.get("FEAT_007")
    cond_required = STAGE_CONDITIONAL_REQUIRED.get(stage, [])
    missing_cond = [fid for fid in cond_required if fid not in set_ids]

    all_missing = missing_hard + missing_cond + missing_soft
    questions = {
        fid: QUESTIONS.get(fid, f"{fid}: {FEATURES.get(fid, {}).get('description', '?')}")
        for fid in all_missing
    }

    return {
        "ok_to_recommend": not missing_hard,
        "missing_hard": missing_hard,
        "missing_conditional": missing_cond,
        "missing_soft": missing_soft,
        "questions": questions,
    }
