"""Rule-based weights mapping (features, value) -> {action_id: score}.

Score range: -1.0 ~ +1.0
- +1.0 = 강력 추천
- +0.5 = 약한 추천
- 0    = 무관 (기본값)
- -0.5 = 약한 반대
- -1.0 = 강력 반대
"""
from collections import defaultdict


def compute_scores(features: dict) -> dict[str, float]:
    """Given extracted feature dict -> dict of {action_id: score}."""
    s: defaultdict[str, float] = defaultdict(float)
    f = features

    stage = f.get("FEAT_007")

    if stage == "awareness":
        for aid in ["ACT_001", "ACT_002", "ACT_003", "ACT_004", "ACT_005", "ACT_007", "ACT_010"]:
            s[aid] += 0.6
        for aid in ["ACT_022", "ACT_023", "ACT_077", "ACT_082", "ACT_088", "ACT_092"]:
            s[aid] -= 0.5

    if stage == "discovery":
        for aid in ["ACT_011", "ACT_012", "ACT_013", "ACT_014", "ACT_015", "ACT_016", "ACT_017", "ACT_018", "ACT_021", "ACT_045"]:
            s[aid] += 0.7
        for aid in ["ACT_077", "ACT_082"]:
            s[aid] -= 0.7

    if stage == "qualification":
        for aid in ["ACT_022", "ACT_023", "ACT_024", "ACT_025", "ACT_026", "ACT_027", "ACT_028"]:
            s[aid] += 0.7

    if stage == "demo":
        for aid in ["ACT_029", "ACT_030", "ACT_031", "ACT_032", "ACT_033", "ACT_034"]:
            s[aid] += 0.7

    if stage == "evaluation":
        for aid in ["ACT_035", "ACT_036", "ACT_046", "ACT_047", "ACT_048", "ACT_049"]:
            s[aid] += 0.5

    if stage == "poc":
        for aid in ["ACT_037", "ACT_038", "ACT_039", "ACT_040", "ACT_041", "ACT_042", "ACT_043"]:
            s[aid] += 0.7

    if stage == "negotiation":
        for aid in ["ACT_059", "ACT_061", "ACT_070", "ACT_071", "ACT_072", "ACT_074", "ACT_077"]:
            s[aid] += 0.6

    if stage == "closing":
        for aid in ["ACT_077", "ACT_078", "ACT_079", "ACT_080", "ACT_081", "ACT_082"]:
            s[aid] += 0.7

    if stage == "onboarding":
        for aid in ["ACT_083", "ACT_084", "ACT_085", "ACT_086"]:
            s[aid] += 0.7

    if stage in ("adoption", "active_customer"):
        for aid in ["ACT_087", "ACT_088"]:
            s[aid] += 0.6

    if stage == "expansion":
        for aid in ["ACT_088", "ACT_089", "ACT_090", "ACT_091"]:
            s[aid] += 0.7

    if stage == "renewal":
        for aid in ["ACT_092", "ACT_088"]:
            s[aid] += 0.8

    # ===== POC STATUS =====
    poc = f.get("FEAT_049")
    if poc == "scoping":
        s["ACT_037"] += 0.9
        s["ACT_038"] += 0.7
    if poc == "agreement_signed":
        s["ACT_039"] += 0.9
    if poc == "running":
        s["ACT_040"] += 0.7
        if not f.get("FEAT_051"):
            s["ACT_041"] += 0.9
        if f.get("FEAT_050") == "low":
            s["ACT_041"] += 0.5
    if poc == "completed_pass":
        s["ACT_043"] += 0.6
        s["ACT_044"] += 0.7
        s["ACT_077"] += 0.8
    if poc == "extended":
        s["ACT_041"] += 0.6
    poc_progress = f.get("FEAT_052")
    if isinstance(poc_progress, (int, float)) and poc_progress >= 50 and not f.get("FEAT_051"):
        s["ACT_041"] += 0.7

    # ===== SECURITY / COMPLIANCE =====
    sec = f.get("FEAT_054")
    ciso = f.get("FEAT_015")
    compliance = f.get("FEAT_056") or []
    sec_docs = f.get("FEAT_055") or []

    if sec == "questionnaire_sent":
        s["ACT_056"] += 0.9
    if sec == "in_review":
        s["ACT_056"] += 0.5

    if ciso == "engaged":
        s["ACT_053"] += 0.6
        s["ACT_054"] += 0.5
    if ciso == "blocking":
        s["ACT_055"] += 0.9
        s["ACT_053"] += 0.7
    if ciso == "not_engaged" and stage in ("evaluation", "poc", "negotiation"):
        s["ACT_053"] += 0.7

    if "k_isms" in compliance and "k_isms" not in sec_docs:
        s["ACT_095"] += 0.95
        s["ACT_053"] += 0.6
    if "soc2" in compliance and "soc2" not in sec_docs:
        s["ACT_053"] += 0.7

    if f.get("FEAT_057"):
        s["ACT_095"] += 0.8
    if f.get("FEAT_058"):
        s["ACT_053"] += 0.5

    # ===== STAKEHOLDER GAPS =====
    if not f.get("FEAT_011") and stage in ("discovery", "qualification", "evaluation"):
        s["ACT_045"] += 0.8
    if f.get("FEAT_012") == "weakening":
        s["ACT_051"] += 1.0
        s["ACT_048"] += 0.7

    if not f.get("FEAT_013") and stage in ("evaluation", "poc", "negotiation"):
        s["ACT_024"] += 0.8

    if f.get("FEAT_021") and f.get("FEAT_021") <= 2 and stage in ("evaluation", "poc", "negotiation"):
        s["ACT_048"] += 0.8

    if not f.get("FEAT_014") and f.get("FEAT_004") in ("100k_to_500k", "over_500k"):
        s["ACT_049"] += 0.6

    if f.get("FEAT_016") and f.get("FEAT_046"):
        s["ACT_057"] += 0.8
        s["ACT_033"] += 0.7

    if f.get("FEAT_017") and f.get("FEAT_044") is False:
        s["ACT_058"] += 0.7

    if f.get("FEAT_019"):
        s["ACT_060"] += 0.8

    if f.get("FEAT_018") == "actively_engaged":
        s["ACT_061"] += 0.7

    # ===== QUALIFICATION GAPS =====
    if f.get("FEAT_037") in ("unknown", "exploring"):
        s["ACT_022"] += 0.5
        s["ACT_060"] += 0.6
    if not f.get("FEAT_040"):
        s["ACT_025"] += 0.4
    if not f.get("FEAT_041") and stage in ("evaluation", "negotiation"):
        s["ACT_025"] += 0.4
        s["ACT_059"] += 0.8

    # ===== DISCOVERY DEPTH =====
    if not f.get("FEAT_032") and stage == "discovery":
        s["ACT_014"] += 0.8
    if f.get("FEAT_032") and not f.get("FEAT_033"):
        s["ACT_015"] += 0.9
    if f.get("FEAT_033") and not f.get("FEAT_034"):
        s["ACT_016"] += 0.8

    if f.get("FEAT_035") in ("low", "none") and stage in ("discovery", "evaluation"):
        s["ACT_017"] += 0.6
        s["ACT_018"] += 0.6

    # ===== TECHNICAL EVAL =====
    if f.get("FEAT_046") and not f.get("FEAT_045"):
        s["ACT_032"] += 0.95
        s["ACT_036"] += 0.7

    if f.get("FEAT_043") is False and stage in ("evaluation",):
        s["ACT_029"] += 0.7
        s["ACT_030"] += 0.7

    # ===== OBJECTIONS =====
    objections = f.get("FEAT_059") or []
    obj_to_action = {
        "price": ["ACT_062", "ACT_070"],
        "no_budget": ["ACT_063", "ACT_060"],
        "no_authority": ["ACT_064", "ACT_024"],
        "wrong_timing": ["ACT_065"],
        "incumbent_satisfied": ["ACT_066", "ACT_067"],
        "competitor_preference": ["ACT_067"],
        "security_concern": ["ACT_068", "ACT_053"],
        "deployment_burden": ["ACT_069"],
        "no_korea_reference": ["ACT_094", "ACT_035"],
    }
    for obj in objections:
        for aid in obj_to_action.get(obj, []):
            s[aid] += 0.85

    # ===== NEGOTIATION =====
    if f.get("FEAT_062"):
        s["ACT_071"] += 0.8
        s["ACT_072"] += 0.7
        s["ACT_073"] += 0.6
    if f.get("FEAT_064"):
        s["ACT_076"] += 0.9

    # ===== CLOSING =====
    if not f.get("FEAT_065") and stage in ("evaluation", "negotiation"):
        s["ACT_077"] += 0.8
    if f.get("FEAT_065") and not f.get("FEAT_067"):
        s["ACT_080"] += 0.7
    if f.get("FEAT_068") and not f.get("FEAT_069"):
        s["ACT_081"] += 0.6

    # ===== STALE DEAL DETECTION =====
    days_in_stage = f.get("FEAT_008")
    if isinstance(days_in_stage, (int, float)) and days_in_stage >= 30 and stage in ("evaluation", "poc", "negotiation"):
        s["ACT_079"] += 0.7
        s["ACT_080"] += 0.6

    days_no_contact = f.get("FEAT_009")
    if isinstance(days_no_contact, (int, float)) and days_no_contact >= 14:
        s["ACT_009"] += 0.6
        s["ACT_006"] += 0.5

    # ===== CHAMPION ENABLEMENT =====
    if f.get("FEAT_011") and stage in ("evaluation", "negotiation"):
        s["ACT_046"] += 0.6
        s["ACT_047"] += 0.5

    # ===== CUSTOMER LIFECYCLE =====
    lifecycle = f.get("FEAT_070")
    usage = f.get("FEAT_071")
    adoption = f.get("FEAT_072")

    if lifecycle == "active_customer":
        s["ACT_088"] += 0.6
    if isinstance(usage, (int, float)) and usage >= 80:
        s["ACT_089"] += 0.95
    if adoption in ("at_risk", "declining"):
        s["ACT_087"] += 0.95
    if "multi_team_usage" in (f.get("FEAT_074") or []):
        s["ACT_090"] += 0.9
    if "new_use_case_mentioned" in (f.get("FEAT_074") or []):
        s["ACT_091"] += 0.8
    if "champion_promoted" in (f.get("FEAT_074") or []):
        s["ACT_093"] += 0.85

    days_renewal = f.get("FEAT_073")
    if isinstance(days_renewal, (int, float)) and 0 <= days_renewal <= 90:
        s["ACT_092"] += 0.95
        s["ACT_088"] += 0.6

    # ===== KOREAN MARKET =====
    if f.get("FEAT_003") == "korea":
        certs = f.get("FEAT_078") or []
        if "k_isms" in certs and "k_isms" not in sec_docs:
            s["ACT_095"] += 0.7
        if any(c in certs for c in ("csap", "fsi", "gov_procurement")):
            s["ACT_100"] += 0.85

        if f.get("FEAT_077") == "considering":
            s["ACT_096"] += 0.95
        elif f.get("FEAT_001") == "enterprise" and f.get("FEAT_077") == "none":
            s["ACT_096"] += 0.7

        if f.get("FEAT_076"):
            if f.get("FEAT_004") == "over_500k":
                s["ACT_097"] += 0.95
            else:
                s["ACT_097"] += 0.7

        if f.get("FEAT_080"):
            s["ACT_098"] += 0.85

        if f.get("FEAT_079"):
            s["ACT_099"] += 0.7

        if not f.get("FEAT_075"):
            if f.get("FEAT_001") == "enterprise":
                s["ACT_094"] += 0.9
            elif f.get("FEAT_001") == "mid_market":
                s["ACT_094"] += 0.5

    # ===== INDUSTRY-MATCHED REFERENCE =====
    if stage in ("evaluation", "poc", "demo") and f.get("FEAT_002"):
        if f.get("FEAT_011") and not f.get("FEAT_048"):
            s["ACT_035"] += 0.7

    # ===== END USER WORKSHOP =====
    if f.get("FEAT_017") and stage in ("evaluation", "demo"):
        if not f.get("FEAT_044"):
            s["ACT_058"] += 0.85
        else:
            s["ACT_058"] += 0.5

    # ===== TRIGGER EVENTS =====
    if f.get("FEAT_023") and lifecycle in ("new_prospect", None):
        triggers = f.get("FEAT_024") or []
        if triggers:
            s["ACT_001"] += 0.6
            s["ACT_009"] += 0.5

    return dict(s)
