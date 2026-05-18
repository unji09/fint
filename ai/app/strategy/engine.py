"""Recommendation engine — given features, return top-N actions with scores and reasons.

Scoring backends:
  "rule" (default): rule-based weights from weights.py
  "ml_distilled":   LightGBM model trained on synthetic records (optional)
"""
import os

from .data import ACTIONS
from .weights import compute_scores


def _compute_with_backend(features: dict, backend: str) -> dict[str, float]:
    if backend == "rule":
        return compute_scores(features)
    if backend == "ml_distilled":
        from .ml.model import compute_scores_ml_distilled
        return compute_scores_ml_distilled(features)
    raise ValueError(f"Unknown backend: {backend!r}. Use 'rule' | 'ml_distilled'.")


def recommend(
    features: dict,
    top_n: int = 5,
    min_score: float = 0.3,
    backend: str | None = None,
) -> list[tuple[dict, float, str]]:
    """Return list of (action_dict, score, reason_str) sorted by score desc."""
    if backend is None:
        backend = os.environ.get("RECOMMENDER_BACKEND", "rule")
    raw_scores = _compute_with_backend(features, backend)
    ranked = sorted(
        ((aid, sc) for aid, sc in raw_scores.items() if sc >= min_score),
        key=lambda x: -x[1],
    )[:top_n]

    results: list[tuple[dict, float, str]] = []
    for aid, sc in ranked:
        action = ACTIONS.get(aid)
        if not action:
            continue
        reason = generate_reason(action, features, sc)
        results.append((action, round(sc, 2), reason))
    return results


_STAGE_KR = {
    "awareness": "어웨어니스", "discovery": "디스커버리", "qualification": "자격검증",
    "demo": "데모", "evaluation": "평가", "poc": "PoC", "negotiation": "협상",
    "closing": "클로징", "onboarding": "온보딩", "adoption": "어댑션",
    "expansion": "확장", "renewal": "갱신", "lost": "실주", "closed_won": "수주",
}
_POC_KR = {
    "scoping": "스코핑 단계", "agreement_signed": "합의서 서명됨", "running": "진행 중",
    "completed_pass": "성공 완료", "completed_fail": "실패 완료",
    "extended": "기간 연장됨", "cancelled": "취소",
}
_CISO_KR = {
    "engaged": "참여 중", "blocking": "차단 중", "informed": "통보 단계", "not_engaged": "미참여",
}
_OBJ_KR = {
    "price": "가격 부담", "no_budget": "예산 미확보", "no_authority": "결정 권한 부족",
    "wrong_timing": "타이밍 문제", "incumbent_satisfied": "기존 솔루션 만족",
    "competitor_preference": "경쟁사 선호", "security_concern": "보안 우려",
    "deployment_burden": "도입 부담", "feature_gap": "기능 부족",
    "vendor_unknown": "벤더 인지도", "no_korea_reference": "한국 레퍼런스 부재",
}

_PERSONA_KR = {
    "Champion": "챔피언", "Economic Buyer": "EB(예산 권한자)", "EB": "EB",
    "CISO": "CISO", "IT Director": "IT 본부장", "IT Manager": "IT 매니저",
    "End User": "End User", "User": "End User", "Procurement": "구매팀",
    "Finance": "Finance", "Legal": "법무", "Executive Sponsor": "임원 sponsor",
    "C-level": "C-level", "DevOps": "DevOps", "Architect": "아키텍트",
    "Security Architect": "보안 아키텍트", "Compliance": "Compliance",
    "Technical Buyer": "기술 결정자", "All": "전체 stakeholder", "Internal use": "내부 사용",
}


def _humanize_persona(personas: list[str]) -> str:
    if not personas:
        return "관련 stakeholder"
    return ", ".join(_PERSONA_KR.get(p, p) for p in personas)


def generate_reason(action: dict, features: dict, score: float) -> str:
    matched = match_triggers_to_features(action, features)
    why = "; ".join(matched) if matched else "현재 상황과 부합"

    persona = action.get("target_persona", [])
    who = _humanize_persona(persona)
    outcome = action.get("expected_outcome", "다음 단계 진행").strip()

    return f"**이유**: {why} · **대상**: {who} · **기대**: {outcome}"


def match_triggers_to_features(action: dict, features: dict) -> list[str]:
    matches: list[str] = []
    f = features
    stage = f.get("FEAT_007")
    a_triggers_text = str(action.get("trigger_signals", [])).lower()
    a_persona_text = str(action.get("target_persona", []))
    a_name = action.get("name", "")
    a_cat = action.get("category", "")

    if stage and stage in a_triggers_text:
        matches.append(f"{_STAGE_KR.get(stage, stage)} 단계")

    poc = f.get("FEAT_049")
    if poc in ("scoping", "agreement_signed", "running", "completed_pass", "extended") \
       and ("PoC" in str(action.get("trigger_signals", [])) or "poc" in a_cat.lower()):
        matches.append(f"PoC {_POC_KR.get(poc, poc)}")

    ciso = f.get("FEAT_015")
    if "CISO" in a_persona_text and ciso in ("engaged", "blocking", "informed"):
        matches.append(f"CISO {_CISO_KR.get(ciso, ciso)}")

    comp = f.get("FEAT_056") or []
    if "k_isms" in comp and ("CISO" in a_persona_text or "Korean" in a_cat or "K-ISMS" in a_name):
        matches.append("K-ISMS 컴플라이언스 요구")
    if "soc2" in comp and "soc2" not in (f.get("FEAT_055") or []) and "CISO" in a_persona_text:
        matches.append("SOC 2 요구")

    if "Champion" in a_persona_text:
        if not f.get("FEAT_011"):
            matches.append("챔피언 미식별")
        elif f.get("FEAT_012") == "weakening":
            matches.append("챔피언 약화 중")

    if "Economic Buyer" in a_persona_text or "EB" in a_persona_text:
        if not f.get("FEAT_013"):
            matches.append("EB 미참여")

    depth = f.get("FEAT_021")
    if ("multi" in a_name.lower() or "Multi-thread" in a_cat) and isinstance(depth, (int, float)) and depth <= 2:
        matches.append(f"멀티스레드 얕음 ({int(depth)}명)")

    if a_cat == "Korean Market Specific":
        if f.get("FEAT_003") == "korea":
            matches.append("한국 시장")
        if f.get("FEAT_080"):
            matches.append("Q4 마감 압박")
        if f.get("FEAT_076") and f.get("FEAT_004") == "over_500k":
            matches.append("본사-한국지사 이중 결정")
        if f.get("FEAT_077") in ("considering", "engaged"):
            matches.append("SI 파트너 협업 가능")

    if stage in ("evaluation", "poc", "demo") and f.get("FEAT_002") and "reference" in a_name.lower():
        matches.append(f"{f.get('FEAT_002')} 산업 reference 필요")

    usage = f.get("FEAT_071")
    if isinstance(usage, (int, float)) and usage >= 80 and "사용량" in a_name:
        matches.append(f"사용량 {int(usage)}% 도달")

    days_renewal = f.get("FEAT_073")
    if isinstance(days_renewal, (int, float)) and days_renewal <= 90 and ("Renewal" in a_name or "renewal" in a_cat.lower()):
        matches.append(f"갱신까지 {int(days_renewal)}일")

    objs = f.get("FEAT_059") or []
    if objs and ("objection" in a_cat.lower() or "Objection" in a_cat):
        objs_kr = [_OBJ_KR.get(o, o) for o in objs]
        matches.append(f"이의제기({', '.join(objs_kr)})")

    if f.get("FEAT_062") and ("할인" in a_name or "discount" in a_name.lower() or "concession" in a_name.lower() or "Multi-year" in a_name):
        matches.append("할인 요구 표면화")

    if f.get("FEAT_068") and not f.get("FEAT_069") and "서면" in a_name:
        matches.append("구두 yes 있음, 서면 미확보")

    days_in_stage = f.get("FEAT_008")
    if isinstance(days_in_stage, (int, float)) and days_in_stage >= 30 \
       and ("Trial close" in a_name or "decision date" in a_name.lower() or "Decision Date" in a_name):
        matches.append(f"이 단계 {int(days_in_stage)}일째")

    return matches[:3]


def explain_recommendation(features: dict, recommendations: list) -> str:
    stage = features.get("FEAT_007", "unknown")
    summary_lines = [f"## 현재 상황 요약", f"- 영업 단계: **{stage}**"]

    if features.get("FEAT_003") == "korea":
        summary_lines.append("- 한국 시장")
    if features.get("FEAT_005"):
        summary_lines.append("- 규제 산업")
    if features.get("FEAT_015") in ("engaged", "blocking"):
        summary_lines.append(f"- CISO 참여 상태: {features.get('FEAT_015')}")
    if features.get("FEAT_049") and features.get("FEAT_049") != "none":
        summary_lines.append(f"- PoC 상태: {features.get('FEAT_049')}")
    if features.get("FEAT_011") is False:
        summary_lines.append("- 챔피언 미식별")
    if features.get("FEAT_013") is False and stage in ("evaluation", "poc", "negotiation"):
        summary_lines.append("- Economic Buyer 미참여")

    summary_lines.append("")
    summary_lines.append(f"## Top {len(recommendations)} 추천 액션")
    for i, (action, score, reason) in enumerate(recommendations, 1):
        summary_lines.append("")
        summary_lines.append(f"**{i}. {action['name']}** (`{action['id']}` · 점수 {score})")
        summary_lines.append(f"   - 카테고리: {action['category']}")
        summary_lines.append(f"   - 근거: {reason}")

    return "\n".join(summary_lines)
