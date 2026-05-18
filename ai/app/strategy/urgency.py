"""각 추천 액션의 긴급도 평가.

긴급도 = score(상황 적합도)와 다른 차원.
  score = "이 액션이 이 상황에 얼마나 적합한가"
  urgency = "이 액션을 얼마나 빨리 해야 하나"
"""
from __future__ import annotations


def evaluate_urgency(features: dict, action: dict) -> dict:
    score = 0.0
    reasons: list[str] = []

    # ---- Case-level 시간 압박 ----
    days_renewal = features.get("FEAT_073")
    if isinstance(days_renewal, (int, float)):
        if days_renewal <= 0:
            score += 5
            reasons.append("갱신 만료됨")
        elif days_renewal <= 30:
            score += 4
            reasons.append(f"갱신 {int(days_renewal)}일 남음")
        elif days_renewal <= 90:
            score += 2
            reasons.append(f"갱신 {int(days_renewal)}일 남음")

    days_in_stage = features.get("FEAT_008")
    if isinstance(days_in_stage, (int, float)):
        if days_in_stage >= 60:
            score += 3
            reasons.append(f"이 단계 {int(days_in_stage)}일째 (stale)")
        elif days_in_stage >= 30:
            score += 1
            reasons.append(f"이 단계 {int(days_in_stage)}일째")

    days_no_contact = features.get("FEAT_009")
    if isinstance(days_no_contact, (int, float)) and days_no_contact >= 14:
        score += 1
        reasons.append(f"마지막 컨택 후 {int(days_no_contact)}일")

    if features.get("FEAT_080"):
        score += 2
        reasons.append("Q4 마감 압박")

    # ---- Case-level 상태 위험 신호 ----
    if features.get("FEAT_012") == "weakening":
        score += 3
        reasons.append("챔피언 약화 중")

    if features.get("FEAT_015") == "blocking":
        score += 3
        reasons.append("CISO 차단 상태")

    if features.get("FEAT_035") == "acute":
        score += 2
        reasons.append("페인 긴급도 acute")

    objs = features.get("FEAT_059") or []
    if len(objs) >= 2:
        score += 2
        reasons.append(f"이의제기 {len(objs)}건")
    elif len(objs) == 1:
        score += 1

    poc_eng = features.get("FEAT_050")
    if poc_eng == "low":
        score += 2
        reasons.append("PoC 참여도 낮음")

    if features.get("FEAT_068") and not features.get("FEAT_069"):
        score += 2
        reasons.append("구두 yes 받았으나 서면 미확보")

    adoption = features.get("FEAT_072")
    if adoption in ("at_risk", "declining"):
        score += 3
        reasons.append("어댑션 위험")

    # ---- Action-level multiplier ----
    stage = features.get("FEAT_007")
    a_cat = action.get("category", "")

    if a_cat == "Closing & MAP" and stage in ("negotiation", "closing"):
        score += 2
    if a_cat == "Expansion & Renewal" and isinstance(days_renewal, (int, float)) and days_renewal <= 90:
        score += 2

    # ---- 등급화 ----
    if score >= 9:
        level, n_stars = "긴급", 5
    elif score >= 6:
        level, n_stars = "긴급", 4
    elif score >= 4:
        level, n_stars = "중요", 3
    elif score >= 2:
        level, n_stars = "보통", 2
    else:
        level, n_stars = "보통", 1

    return {
        "level": level,
        "score": round(score, 1),
        "n_stars": n_stars,
        "reasons": reasons,
    }
