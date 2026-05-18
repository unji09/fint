"""LLM-based / Mock feature extraction from natural language context.

Backends:
  - "llm":  OpenAI function calling via LLMClient (Tier 1+2 features only)
  - "mock": keyword/regex-based deterministic extraction (no API call)
  - "dummy": hardcoded dummy features (테스트용)

Both llm/mock backends extract only Tier 1+2 priority features (32 of 80)
to reduce API tokens, parser errors, and LLM hallucination risk.
"""
import json
import logging
import re

from app.clients.llm import LLMClient
from .data import FEATURES

logger = logging.getLogger(__name__)

# ============================================================
# Tier 1+2 priority feature IDs (from v02 analysis)
# ============================================================
TIER_1_IDS = [
    "FEAT_007", "FEAT_059", "FEAT_011", "FEAT_056", "FEAT_049",
    "FEAT_062", "FEAT_002", "FEAT_048", "FEAT_044", "FEAT_032", "FEAT_033",
]
TIER_2_IDS = [
    "FEAT_046", "FEAT_012", "FEAT_018", "FEAT_065", "FEAT_074",
    "FEAT_015", "FEAT_004", "FEAT_035", "FEAT_071", "FEAT_072",
    "FEAT_054", "FEAT_045", "FEAT_064", "FEAT_014",
]
KOREAN_CONTEXT_BOOST = [
    "FEAT_003", "FEAT_075", "FEAT_076", "FEAT_077", "FEAT_078", "FEAT_079", "FEAT_080",
]

_all_priority = TIER_1_IDS + TIER_2_IDS + KOREAN_CONTEXT_BOOST
PRIORITY_IDS: list[str] = list(dict.fromkeys(_all_priority))
PRIORITY_FEATURES: list[dict] = [FEATURES[fid] for fid in PRIORITY_IDS if fid in FEATURES]


# ============================================================
# MOCK BACKEND — keyword/regex extraction
# ============================================================
_MOCK_PATTERNS: list[tuple[str, str, object]] = [
    # ---- FEAT_007 current_stage ----
    (r"갱신|renewal", "FEAT_007", "renewal"),
    (r"확장\s*단계|expansion\s*stage|업셀\s*기회|upsell\s*opportunity|cross-?sell\s*기회|확장\s*시점", "FEAT_007", "expansion"),
    (r"온보딩|onboarding", "FEAT_007", "onboarding"),
    (r"클로징|closing|계약\s*체결", "FEAT_007", "closing"),
    (r"협상|negotiation|네고|가격\s*협상", "FEAT_007", "negotiation"),
    (r"poc\s*(중|진행|시작|스코핑|스코프)|proof\s*of\s*concept", "FEAT_007", "poc"),
    (r"평가|evaluation|벤치마크|benchmark", "FEAT_007", "evaluation"),
    (r"데모(\s*완료|\s*예정)?|demo", "FEAT_007", "demo"),
    (r"자격(\s*검증)?|qualification|bant|meddic", "FEAT_007", "qualification"),
    (r"디스커버리|discovery|니즈\s*파악|페인\s*파악|검토\s*중|검토\s*시작|검토(?!\s*완료)", "FEAT_007", "discovery"),
    (r"리드\s*발굴|콜드|cold|prospect|어웨어니스|awareness", "FEAT_007", "awareness"),

    # ---- FEAT_002 industry ----
    (r"보험|은행|금융|finance|insurance|bank", "FEAT_002", "finance"),
    (r"의료|병원|healthcare|hospital|제약", "FEAT_002", "healthcare"),
    (r"제조|자동차|반도체|화학|manufacturing|automotive", "FEAT_002", "manufacturing"),
    (r"공공|정부|public sector|government", "FEAT_002", "public_sector"),
    (r"리테일|유통|retail|이커머스", "FEAT_002", "retail"),
    (r"saas|소프트웨어|tech", "FEAT_002", "tech_saas"),
    (r"통신|텔코|telco|telecom", "FEAT_002", "telco"),
    (r"에너지|energy|전력|발전", "FEAT_002", "energy"),
    (r"교육|education|대학|학교", "FEAT_002", "education"),

    # ---- FEAT_003 geography ----
    (r"한국|국내|korea|korean", "FEAT_003", "korea"),
    (r"미국|us\b|미\s*기업|america", "FEAT_003", "us"),
    (r"유럽|europe|eu\b", "FEAT_003", "europe"),
    (r"아태|apac|아시아", "FEAT_003", "apac_ex_korea"),
    (r"글로벌|global", "FEAT_003", "global"),

    # ---- FEAT_001 company_size ----
    (r"대기업|enterprise|대형|글로벌\s*기업", "FEAT_001", "enterprise"),
    (r"mid\s*market|중견|중간\s*규모", "FEAT_001", "mid_market"),
    (r"smb|중소|스타트업|startup|소기업", "FEAT_001", "smb"),

    # ---- FEAT_004 estimated_acv ----
    (r"\$?500k\s*이상|\$?500k\+|500,?000\s*달러\s*이상|over\s*500k|\d+M\s*달러", "FEAT_004", "over_500k"),
    (r"\$?100k.*\$?500k|10만.*50만|100k\s*to\s*500k", "FEAT_004", "100k_to_500k"),
    (r"\$?10k.*\$?100k|1만.*10만|10k\s*to\s*100k", "FEAT_004", "10k_to_100k"),
    (r"under\s*10k|소액|\$?10k\s*미만", "FEAT_004", "under_10k"),

    # ---- FEAT_011 champion_identified ----
    (r"챔피언이?\s*(아직\s*)?(없|미식별)|no\s*champion|챔피언\s*식별\s*전", "FEAT_011", False),
    (r"챔피언|champion|내부\s*지지자|사내\s*추천자|it\s*본부장|본부\s*컨택", "FEAT_011", True),

    # ---- FEAT_012 champion_strength ----
    (r"챔피언\s*약화|챔피언\s*흔들|champion\s*weakening", "FEAT_012", "weakening"),
    (r"강한\s*챔피언|strong\s*champion|챔피언\s*강력", "FEAT_012", "strong"),

    # ---- FEAT_015 ciso_engaged ----
    (r"ciso(가|는)?\s*(반대|차단|블록|blocking)", "FEAT_015", "blocking"),
    (r"ciso\s*미팅|ciso(가|는)?\s*(참여|engaged|검토\s*중)", "FEAT_015", "engaged"),
    (r"ciso(에게|한테)?\s*(알림|informed|공유)", "FEAT_015", "informed"),

    # ---- FEAT_018 procurement_engaged ----
    (r"구매팀\s*적극|procurement\s*active|조달\s*협상", "FEAT_018", "actively_engaged"),
    (r"구매팀\s*인지|procurement\s*informed", "FEAT_018", "informed"),

    # ---- FEAT_032 spin_problem_identified ----
    (r"페인\s*포인트|pain\s*point|문제점\s*식별|구체적\s*페인|문제는", "FEAT_032", True),

    # ---- FEAT_033 spin_implication_quantified ----
    (r"\d+시간|\d+\s*hour|\d+%\s*절감|연간\s*\$?\d+|월\s*\d+만", "FEAT_033", True),
    (r"비즈니스\s*영향\s*정량화|business\s*impact\s*quantified", "FEAT_033", True),

    # ---- FEAT_046 integration_concern_raised ----
    (r"통합\s*우려|integration\s*concern|아키텍처\s*검토|기존\s*시스템(\s*연동|\s*통합|\s*마이그레이션)?|sap\s*통합|마이그레이션", "FEAT_046", True),

    # ---- FEAT_044 demo_was_customized ----
    (r"커스텀\s*데모|맞춤\s*데모|customized\s*demo", "FEAT_044", True),
    (r"표준\s*데모|generic\s*demo|일반\s*데모", "FEAT_044", False),

    # ---- FEAT_048 reference_call_completed ----
    (r"레퍼런스\s*콜\s*완료|reference\s*call\s*done|기존\s*고객\s*소개", "FEAT_048", True),

    # ---- FEAT_049 poc_status ----
    (r"poc\s*완료\s*성공|poc\s*passed", "FEAT_049", "completed_pass"),
    (r"poc\s*완료\s*실패|poc\s*failed", "FEAT_049", "completed_fail"),
    (r"poc\s*진행\s*중|poc\s*running", "FEAT_049", "running"),
    (r"poc\s*합의|poc\s*agreement|poc\s*scope\s*확정", "FEAT_049", "agreement_signed"),
    (r"poc\s*스코핑|poc\s*scoping|poc\s*다음주", "FEAT_049", "scoping"),
    (r"poc\s*연장|poc\s*extended", "FEAT_049", "extended"),

    # ---- FEAT_054 security_review_status ----
    (r"보안\s*리뷰\s*통과|security\s*passed", "FEAT_054", "passed"),
    (r"보안\s*검토\s*중|security\s*in\s*review", "FEAT_054", "in_review"),
    (r"보안\s*질문지|security\s*questionnaire", "FEAT_054", "questionnaire_sent"),
    (r"보안\s*블록|security\s*blocked", "FEAT_054", "blocked"),

    # ---- FEAT_056 compliance_framework_required (multi) ----
    (r"k-?isms", "FEAT_056", "k_isms"),
    (r"isms-?p", "FEAT_056", "isms_p"),
    (r"soc\s*2|soc2", "FEAT_056", "soc2"),
    (r"iso\s*27001", "FEAT_056", "iso27001"),
    (r"금융보안원|fsi\s*가이드라인", "FEAT_056", "fsi_guideline"),
    (r"csap|클라우드\s*보안\s*인증", "FEAT_056", "csap"),
    (r"개인정보보호법|pipa", "FEAT_056", "pipa"),
    (r"gdpr", "FEAT_056", "gdpr"),
    (r"hipaa", "FEAT_056", "hipaa"),

    # ---- FEAT_057 data_residency_concern ----
    (r"데이터\s*국내\s*보관|data\s*residency|국내\s*리전", "FEAT_057", True),

    # ---- FEAT_059 active_objections (multi) ----
    (r"가격이?\s*비싸|too\s*expensive|예산\s*부담", "FEAT_059", "price"),
    (r"예산\s*없|no\s*budget|예산\s*미확보", "FEAT_059", "no_budget"),
    (r"권한\s*없|no\s*authority|결정권자\s*아님", "FEAT_059", "no_authority"),
    (r"타이밍\s*안\s*맞|wrong\s*timing|시기상조|아직\s*이르", "FEAT_059", "wrong_timing"),
    (r"기존\s*솔루션\s*만족|incumbent\s*satisfied|현행\s*만족", "FEAT_059", "incumbent_satisfied"),
    (r"경쟁사\s*선호|competitor\s*preference|다른\s*벤더\s*선호", "FEAT_059", "competitor_preference"),
    (r"보안\s*우려|security\s*concern", "FEAT_059", "security_concern"),
    (r"한국\s*레퍼런스\s*없|no\s*korea\s*reference|국내\s*사례\s*없", "FEAT_059", "no_korea_reference"),
    (r"기능\s*부족|feature\s*gap|기능이?\s*없", "FEAT_059", "feature_gap"),

    # ---- FEAT_062 discount_requested ----
    (r"할인\s*요구|discount\s*request|할인\s*요청|네고\s*요청", "FEAT_062", True),

    # ---- FEAT_064 marketplace_eligible ----
    (r"aws\s*마켓플레이스|azure\s*marketplace|gcp\s*marketplace|마켓플레이스\s*결제", "FEAT_064", True),

    # ---- FEAT_065 map_exists ----
    (r"mutual\s*action\s*plan|map\s*작성|마일스톤\s*계획", "FEAT_065", True),

    # ---- FEAT_071 usage_vs_plan_limit_pct ----
    (r"사용량\s*(\d+)%|usage\s*(\d+)%|(\d+)%\s*used", "FEAT_071", "REGEX:int"),

    # ---- FEAT_072 adoption_health ----
    (r"채택\s*위험|at\s*risk|어댑션\s*위험", "FEAT_072", "at_risk"),
    (r"채택\s*감소|declining|사용량\s*감소", "FEAT_072", "declining"),
    (r"건강한\s*어댑션|healthy\s*adoption", "FEAT_072", "healthy"),

    # ---- FEAT_074 expansion_signals_present (multi) ----
    (r"다른\s*팀\s*사용|multi\s*team", "FEAT_074", "multi_team_usage"),
    (r"새로운\s*유스케이스|new\s*use\s*case", "FEAT_074", "new_use_case_mentioned"),
    (r"limit\s*근접|한도\s*근접", "FEAT_074", "limit_approaching"),

    # ---- FEAT_014 executive_sponsor_engaged ----
    (r"본사\s*임원|exec\s*sponsor|c-?level\s*sponsor", "FEAT_014", True),

    # ---- FEAT_045 se_engaged ----
    (r"sales\s*engineer|se\s*투입|기술\s*엔지니어\s*동행", "FEAT_045", True),

    # ---- FEAT_035 pain_urgency ----
    (r"긴급|urgent|acute|당장", "FEAT_035", "acute"),
    (r"moderate|중간\s*급박", "FEAT_035", "moderate"),

    # ---- FEAT_075 korea_local_reference_exists ----
    (r"한국\s*레퍼런스\s*(없|부족)|국내\s*사례\s*(없|부족)|첫\s*한국\s*도입|한국\s*첫\s*도입", "FEAT_075", False),
    (r"한국\s*레퍼런스\s*(있|보유|풍부)|국내\s*동종\s*사례", "FEAT_075", True),

    # ---- FEAT_076 hq_korea_dual_decision ----
    (r"본사.*한국지사|본사.*한국\s*지사|hq.*korea\s*dual|글로벌\s*본사.*한국|본사\s*승인.*한국|한국지사.*본사", "FEAT_076", True),

    # ---- FEAT_077 si_partner_involved ----
    (r"si\s*파트너\s*주도|si\s*led", "FEAT_077", "leading"),
    (r"삼성sds|lg\s*cns|sk\s*c&c|메가존|베스핀|딜로이트|deloitte|kpmg|ey|pwc", "FEAT_077", "considering"),
    (r"si\s*파트너\s*검토|si\s*파트너십\s*활용\s*가능", "FEAT_077", "considering"),
    (r"si\s*파트너\s*협업\s*중|si\s*engaged", "FEAT_077", "engaged"),

    # ---- FEAT_078 korean_certification_required (multi) ----
    (r"k-?isms", "FEAT_078", "k_isms"),
    (r"isms-?p", "FEAT_078", "isms_p"),
    (r"csap", "FEAT_078", "csap"),
    (r"금융보안원|fsi", "FEAT_078", "fsi"),
    (r"정부\s*조달|gov_procurement|나라장터", "FEAT_078", "gov_procurement"),

    # ---- FEAT_079 korean_language_support_concern ----
    (r"한국어\s*(자료|ui|지원)\s*(우려|문제|필요)|korean\s*language\s*(concern|support)", "FEAT_079", True),

    # ---- FEAT_080 fiscal_year_end_pressure ----
    (r"q4\s*(마감|예산|드라이브|push)|회계연도\s*마감|fiscal\s*year\s*end|연말\s*마감", "FEAT_080", True),
]

_FEAT_TYPE_CACHE: dict[str, str] = {}


def _get_feat_type(fid: str) -> str:
    if fid not in _FEAT_TYPE_CACHE:
        feat = FEATURES.get(fid, {})
        _FEAT_TYPE_CACHE[fid] = feat.get("type", "categorical")
    return _FEAT_TYPE_CACHE[fid]


def extract_features_mock(text: str) -> dict:
    """Run all mock regex patterns over text and assemble features dict."""
    txt = text.lower()
    out: dict = {}

    for pattern, fid, value in _MOCK_PATTERNS:
        feat_type = _get_feat_type(fid)
        m = re.search(pattern, txt, re.IGNORECASE)
        if not m:
            continue

        if feat_type == "multi_select":
            cur = out.get(fid, [])
            if value not in cur:
                cur.append(value)
            out[fid] = cur
        else:
            if fid not in out:
                if value == "REGEX:int":
                    num_str = next((g for g in m.groups() if g), None)
                    if num_str:
                        out[fid] = int(num_str)
                else:
                    out[fid] = value

    return out


# ============================================================
# LLM BACKEND — OpenAI function calling via LLMClient
# ============================================================

def _build_tool_schema() -> dict:
    """Build OpenAI-format tool schema for Tier 1+2 features only."""
    properties = {}
    for f in PRIORITY_FEATURES:
        prop: dict = {"description": f.get("description", "")}
        t = f["type"]
        if t == "boolean":
            prop["type"] = "boolean"
        elif t == "categorical":
            prop["type"] = "string"
            prop["enum"] = f["values"]
        elif t == "multi_select":
            prop["type"] = "array"
            prop["items"] = {"type": "string", "enum": f["values"]}
        elif t == "numeric":
            prop["type"] = "number"
            if "range" in f:
                prop["minimum"] = f["range"][0]
                prop["maximum"] = f["range"][1]
        properties[f["id"]] = prop

    return {
        "type": "function",
        "function": {
            "name": "extract_sales_features",
            "description": (
                "Extract structured sales features from a natural-language sales situation. "
                "Only extract features that are explicitly stated or strongly implied. "
                "Omit fields when unclear (do NOT guess)."
            ),
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": [],
            },
        },
    }


_SYSTEM_PROMPT = (
    "You are a B2B sales situation analyst. Extract structured features "
    "from natural-language sales descriptions. Rules:\n"
    "1. Only set a feature if explicitly mentioned or strongly implied.\n"
    "2. Do NOT guess; omit unclear fields.\n"
    "3. Use exactly the enum values defined in the tool schema.\n"
    "4. Korean and English input both acceptable; output uses enum values verbatim.\n"
    "5. For multi_select fields, return a list; empty list if nothing matches."
)

_TOOL_SCHEMA = _build_tool_schema()


async def extract_features(context: str, llm: LLMClient) -> dict:
    """Extract priority features from context text via LLM function calling."""
    messages = [
        {"role": "system", "content": _SYSTEM_PROMPT},
        {"role": "user", "content": context},
    ]

    tool_choice = {"type": "function", "function": {"name": "extract_sales_features"}}

    try:
        resp = await llm.chat_with_tools(
            messages,
            tools=[_TOOL_SCHEMA],
        )
    except Exception:
        logger.exception("LLM function calling failed, falling back to mock parser")
        return extract_features_mock(context)

    if hasattr(resp, "tool_calls") and resp.tool_calls:
        try:
            return json.loads(resp.tool_calls[0].function.arguments)
        except (json.JSONDecodeError, AttributeError, IndexError):
            logger.error("Failed to parse tool_calls response")
            return extract_features_mock(context)

    logger.warning("LLM did not return tool_calls, falling back to mock parser")
    return extract_features_mock(context)


# ============================================================
# 테스트용 더미 피처 (OPENAI_API_KEY 토큰 절약)
# ============================================================

_DUMMY_FEATURES: dict = {
    "FEAT_001": "enterprise",
    "FEAT_002": "technology",
    "FEAT_003": "korea",
    "FEAT_005": True,
    "FEAT_007": "evaluation",
    "FEAT_008": 15,
    "FEAT_011": True,
    "FEAT_012": "stable",
    "FEAT_013": False,
    "FEAT_015": "not_engaged",
    "FEAT_032": True,
    "FEAT_033": True,
    "FEAT_049": None,
    "FEAT_056": [],
    "FEAT_059": [],
    "FEAT_071": None,
    "FEAT_080": False,
}


def extract_features_dummy(context: str) -> dict:
    """LLM 호출 없이 하드코딩된 더미 피처를 반환한다 (테스트용)."""
    logger.info("Using DUMMY features (LLM call skipped). context length=%d", len(context))
    return dict(_DUMMY_FEATURES)
