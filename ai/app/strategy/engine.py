"""Recommendation engine — given features, return top-N actions with scores and reasons."""
from .data import ACTIONS
from .weights import compute_scores


def recommend(features: dict, top_n: int = 5, min_score: float = 0.3) -> list[tuple[dict, float, str]]:
    """Return list of (action_dict, score, reason_str) sorted by score desc."""
    raw_scores = compute_scores(features)
    ranked = sorted(
        ((aid, sc) for aid, sc in raw_scores.items() if sc >= min_score),
        key=lambda x: -x[1],
    )[:top_n]

    results: list[tuple[dict, float, str]] = []
    for aid, sc in ranked:
        action = ACTIONS.get(aid)
        if not action:
            continue
        reason = _generate_reason(action, features)
        results.append((action, round(sc, 2), reason))
    return results


def _generate_reason(action: dict, features: dict) -> str:
    outcome = action.get("expected_outcome", "")
    persona = action.get("target_persona", [])
    persona_str = ", ".join(persona) if persona else "관련 stakeholder"

    matched = _match_triggers(action, features)
    matched_str = ", ".join(matched) if matched else "현재 상황 부합"

    return f"[{matched_str}] → {persona_str} 대상. 기대: {outcome}"


def _match_triggers(action: dict, features: dict) -> list[str]:
    matches: list[str] = []
    f = features

    if any("PoC" in t for t in action.get("trigger_signals", [])):
        if f.get("FEAT_049") in ("scoping", "agreement_signed", "running"):
            matches.append(f"PoC 단계={f.get('FEAT_049')}")

    stage = f.get("FEAT_007")
    if stage and stage in str(action.get("trigger_signals", [])).lower():
        matches.append(f"단계={stage}")

    if "CISO" in str(action.get("target_persona", [])):
        if f.get("FEAT_015") in ("engaged", "blocking"):
            matches.append(f"CISO={f.get('FEAT_015')}")
        if "k_isms" in (f.get("FEAT_056") or []):
            matches.append("K-ISMS 요구")

    if "Champion" in str(action.get("target_persona", [])):
        if not f.get("FEAT_011"):
            matches.append("챔피언 미식별")
        elif f.get("FEAT_012") == "weakening":
            matches.append("챔피언 약화")

    if "Economic Buyer" in str(action.get("target_persona", [])) or "EB" in str(action.get("target_persona", [])):
        if not f.get("FEAT_013"):
            matches.append("EB 미참여")

    if action.get("category") == "Korean Market Specific":
        if f.get("FEAT_003") == "korea":
            matches.append("한국 시장")
        if f.get("FEAT_080"):
            matches.append("Q4 압박")

    objections = f.get("FEAT_059") or []
    if objections and "objection" in action.get("category", "").lower():
        matches.append(f"objection={','.join(objections[:2])}")

    return matches[:3]
