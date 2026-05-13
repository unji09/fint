"""DART 공시 유형 분류 및 핵심 값 추출.

report_nm 패턴 매칭으로 공시를 분류하고, 카테고리별로
영업 시그널에 필요한 핵심 값만 추출한다.
"""
from __future__ import annotations

import re
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class DisclosureClassification:
    category: str
    priority: int
    label: str
    trigger_event_types: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class DisclosureAnalysis:
    classification: DisclosureClassification
    extracted_values: dict[str, str]
    trigger_event_types: list[str]
    signal_summary: str


# ── 분류 규칙 (우선순위 순) ──

_RULES: list[tuple[re.Pattern[str], DisclosureClassification]] = [
    # P0
    (re.compile(r"사업보고서|반기보고서|분기보고서"),
     DisclosureClassification("periodic_report", 0, "정기보고서", ("earnings_commentary",))),
    (re.compile(r"타법인주식.*취득|합병.*결정|분할.*결정|영업양수도"),
     DisclosureClassification("ma", 0, "M&A/합병/분할", ("ma_announcement",))),
    (re.compile(r"대표이사.*변경|대표집행임원.*변경"),
     DisclosureClassification("executive_change", 0, "대표이사변경",
                              ("leadership_change", "new_executive_hire"))),
    # P1
    (re.compile(r"유상증자|전환사채.*발행|신주인수권부사채.*발행|무상증자"),
     DisclosureClassification("fundraising", 1, "자금조달", ("funding_round",))),
    (re.compile(r"판매.공급계약|수주공시"),
     DisclosureClassification("major_contract", 1, "대규모계약/수주", ("product_launch",))),
    (re.compile(r"공정공시|자율공시"),
     DisclosureClassification("voluntary_disclosure", 1, "자율/공정공시", ())),
    # P2
    (re.compile(r"대량보유|최대주주.*변경"),
     DisclosureClassification("ownership_change", 2, "지분변동", ("leadership_change",))),
    (re.compile(r"소송|행정처분|제재"),
     DisclosureClassification("litigation", 2, "소송/제재",
                              ("security_incident", "regulatory_change"))),
    (re.compile(r"임원.주요주주|사외이사.+선임|사외이사.+해임|감사.+선임|감사.+해임"),
     DisclosureClassification("officer_change", 2, "임원변동", ("new_executive_hire",))),
    # P3
    (re.compile(r"자기주식|배당|주주총회|감자"),
     DisclosureClassification("other_financial", 3, "기타재무", ())),
]

_DEFAULT = DisclosureClassification("other", 3, "기타", ())
_CORRECTION_PREFIX = re.compile(r"^\[[^\]]*정정[^\]]*\]\s*")


def classify(report_nm: str) -> DisclosureClassification:
    cleaned = _CORRECTION_PREFIX.sub("", report_nm.strip())
    for pattern, cls in _RULES:
        if pattern.search(cleaned):
            return cls
    return _DEFAULT


# ── 카테고리별 추출 패턴 ──

_NUM = r"[\d,]+"
_UNIT = r"(백만원|억원|천원|원)?"
_SEP = r"[：:\s]+"
_SEP_OPT = r"[：:\s]*"

_COMMON_FINANCIAL: list[tuple[str, re.Pattern[str]]] = [
    ("매출액", re.compile(rf"매출액[^\d]{{0,20}}({_NUM})\s*{_UNIT}")),
    ("영업이익", re.compile(rf"영업이익[^\d]{{0,20}}({_NUM})\s*{_UNIT}")),
    ("당기순이익", re.compile(rf"당기순이익[^\d]{{0,20}}({_NUM})\s*{_UNIT}")),
]

_EXTRACT: dict[str, list[tuple[str, re.Pattern[str]]]] = {
    "periodic_report": [
        ("직원수", re.compile(rf"(?:직원|종업원)\s*수[^\d]{{0,10}}({_NUM})\s*명?")),
    ],
    "ma": [
        ("취득금액", re.compile(
            rf"(?:취득금액|인수금액|투자금액)[^\d]{{0,20}}({_NUM})\s*(백만원|억원|천원|원)?")),
        ("취득대상", re.compile(
            rf"(?:발행회사|취득대상|인수대상|대상회사){_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
        ("취득목적", re.compile(
            rf"(?:취득목적|인수목적){_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
    ],
    "executive_change": [
        ("성명", re.compile(rf"성명{_SEP}(\S+)")),
        ("변경사유", re.compile(rf"변경\s*사유{_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
        ("변경일자", re.compile(rf"변경\s*일자{_SEP}([\d.\-/]+)")),
    ],
    "fundraising": [
        ("발행가액", re.compile(
            rf"(?:발행가액|발행금액|증자규모)[^\d]{{0,20}}({_NUM})\s*(백만원|억원|천원|원)?")),
        ("자금사용목적", re.compile(
            rf"(?:자금의?\s*사용\s*목적|자금용도){_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
    ],
    "major_contract": [
        ("계약금액", re.compile(
            rf"계약금액[^\d]{{0,20}}({_NUM})\s*(백만원|억원|천원|원)?")),
        ("계약상대방", re.compile(
            rf"계약\s*상대\s*방?{_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
        ("매출액대비", re.compile(rf"매출액\s*대비{_SEP_OPT}([\d.]+)\s*%")),
    ],
    "officer_change": [
        ("성명", re.compile(rf"(?:성명|성명\s*\(명칭\)){_SEP}(\S+)")),
        ("직위", re.compile(rf"직위{_SEP}(\S+)")),
        ("선해임구분", re.compile(rf"선임[ㆍ·]?해임\s*구분{_SEP}(\S+)")),
        ("선해임사유", re.compile(rf"선해임\s*사유{_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
        ("보고자", re.compile(rf"보고자{_SEP}(\S+)")),
        ("변동주식수", re.compile(rf"변동\s*주식\s*수[^\d]{{0,10}}({_NUM})")),
        ("변동사유", re.compile(rf"변동\s*사유{_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
    ],
    "ownership_change": [
        ("보고자", re.compile(rf"보고자{_SEP}(\S+)")),
        ("보유주식등의수", re.compile(rf"(?:보유|소유)\s*주식\s*(?:등의\s*)?수[^\d]{{0,10}}({_NUM})")),
        ("보유비율", re.compile(rf"(?:지분\s*율|보유\s*비율|소유\s*비율){_SEP_OPT}([\d.]+)\s*(%)")),
    ],
    "litigation": [
        ("소송금액", re.compile(
            rf"(?:소송|청구|처분)\s*금액[^\d]{{0,20}}({_NUM})\s*{_UNIT}")),
        ("소송상대방", re.compile(
            rf"(?:소송|상대)\s*상대\s*방?{_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
        ("제재기관", re.compile(rf"제재\s*기관{_SEP}(\S+)")),
        ("소송내용", re.compile(
            rf"(?:소송|처분|제재)\s*내용{_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
        ("판결일자", re.compile(rf"(?:판결|처분)\s*일자{_SEP}([\d.\-/]+)")),
    ],
    "other_financial": [
        ("취득주식수", re.compile(rf"취득\s*(?:예정\s*)?주식\s*수[^\d]{{0,10}}({_NUM})")),
        ("취득금액", re.compile(
            rf"취득\s*(?:예정\s*)?금액[^\d]{{0,20}}({_NUM})\s*{_UNIT}")),
        ("취득목적", re.compile(rf"취득\s*목적{_SEP}(.+?)(?:\s+\d+\.|\s*$)")),
        ("주당배당금", re.compile(rf"주당\s*배당금[^\d]{{0,10}}({_NUM})\s*{_UNIT}")),
        ("배당총액", re.compile(rf"배당\s*총액[^\d]{{0,20}}({_NUM})\s*{_UNIT}")),
        ("감자주식수", re.compile(rf"감자\s*주식\s*수[^\d]{{0,10}}({_NUM})")),
        ("감자비율", re.compile(rf"감자\s*비율{_SEP_OPT}([\d.]+)\s*%")),
    ],
}

_VOLUNTARY_KEYWORDS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"채용|인력\s*(?:확충|충원|채용)"), "채용확대"),
    (re.compile(r"신규\s*사업|사업\s*진출|신제품"), "신규사업"),
    (re.compile(r"해외\s*(?:진출|법인|확장)"), "해외진출"),
    (re.compile(r"전략적?\s*제휴|MOU|업무\s*협약"), "전략제휴"),
    (re.compile(r"투자\s*(?:계획|결정|확대)|설비\s*투자"), "투자계획"),
]


def _match_value(m: re.Match[str]) -> str:
    value = m.group(1).strip()
    unit = ""
    if m.lastindex and m.lastindex >= 2 and m.group(2):
        unit = m.group(2).strip()
    return f"{value}{unit}" if unit else value


def extract_key_values(category: str, content: str | None) -> dict[str, str]:
    if not content:
        return {}

    result: dict[str, str] = {}

    if category == "voluntary_disclosure":
        detected = [label for pat, label in _VOLUNTARY_KEYWORDS if pat.search(content)]
        if detected:
            result["감지키워드"] = ", ".join(detected)

    for label, pat in _EXTRACT.get(category, []):
        m = pat.search(content)
        if m:
            result[label] = _match_value(m)

    for label, pat in _COMMON_FINANCIAL:
        if label not in result:
            m = pat.search(content)
            if m:
                result[label] = _match_value(m)

    return result


def build_signal_summary(
    classification: DisclosureClassification,
    report_nm: str,
    extracted_values: dict[str, str],
) -> str:
    parts = [f"[{classification.label}] {report_nm}"]
    if extracted_values:
        kv_str = ", ".join(f"{k}: {v}" for k, v in extracted_values.items())
        parts.append(kv_str)
    return " | ".join(parts)


def analyze(
    report_nm: str,
    content: str | None,
    *,
    flr_nm: str | None = None,
) -> DisclosureAnalysis:
    classification = classify(report_nm)
    extracted = extract_key_values(classification.category, content)
    if classification.category == "officer_change":
        if flr_nm:
            reporter = extracted.get("보고자", "")
            if not reporter or "본인" in reporter:
                extracted["보고자"] = flr_nm
        if "특정증권" in report_nm:
            extracted.pop("성명", None)
    summary = build_signal_summary(classification, report_nm, extracted)
    return DisclosureAnalysis(
        classification=classification,
        extracted_values=extracted,
        trigger_event_types=list(classification.trigger_event_types),
        signal_summary=summary,
    )