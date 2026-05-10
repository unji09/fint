import hashlib
import re
from typing import cast

from fastapi import APIRouter, Depends
from redis.asyncio import Redis

from app.clients import (
    get_ocr_client,
    get_openai_client,
    get_s3_client,
)
from app.clients.ocr import OcrBox, OcrClient
from app.clients.openai import OpenAIClient
from app.clients.s3 import S3Client
from app.core.config import Settings, get_settings
from app.core.errors import BusinessException, CommonErrorCode
from app.core.redis import get_redis
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.schemas.ocr import (
    BusinessCardClassification,
    BusinessCardOcrRequest,
    BusinessCardOcrResponse,
)

router = APIRouter(prefix="/api/v1/ocr", tags=["OCR"])

_EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")

_PHONE_RE = re.compile(r"(?:01[016789](?:[\s.-]?\d{3,4}){2}|0\d{1,2}(?:[\s.-]?\d{3,4}){2})")

_EMAIL_LABEL_PREFIXES: tuple[str, ...] = ("E-mail", "Email", "Mail", "Tel", "Fax", "M", "P", "F", "T")

_DOMAIN_TAIL_RE = re.compile(r"(?:com|kr|net|org|io|co|gov|edu)$", re.IGNORECASE)
_LABEL_PREFIX_RE = re.compile(r"^[A-Z]\s+\S")

_TITLE_DICT: tuple[str, ...] = (
    "대표이사",
    "대표",
    "부대표",
    "회장",
    "부회장",
    "사장",
    "부사장",
    "전무",
    "상무",
    "이사",
    "감사",
    "임원",
    "고문",
    "본부장",
    "부본부장",
    "실장",
    "부실장",
    "센터장",
    "지사장",
    "지점장",
    "팀장",
    "부팀장",
    "파트장",
    "그룹장",
    "부서장",
    "부장",
    "차장",
    "과장",
    "대리",
    "주임",
    "사원",
    "인턴",
    "수석",
    "책임",
    "선임",
    "주임연구원",
    "책임연구원",
    "수석연구원",
    "선임연구원",
    "연구원",
    "연구위원",
    "기술위원",
    "매니저",
    "프로",
    "스태프",
    "디자이너",
    "개발자",
    "기획자",
    "PM",
    "PO",
    "프로듀서",
    "컨설턴트",
    "어드바이저",
    "에반젤리스트",
    "아키텍트",
    "엔지니어",
    "CEO",
    "CTO",
    "CFO",
    "COO",
    "CIO",
    "CMO",
    "CSO",
    "CDO",
    "CPO",
    "CHRO",
    "President",
    "VP",
    "AVP",
    "EVP",
    "SVP",
    "Founder",
    "Co-Founder",
    "Owner",
    "Chairman",
    "Partner",
    "Executive",
    "Director",
    "Manager",
    "Lead",
    "Head",
    "Chief",
    "Senior",
    "Junior",
    "Staff",
    "Principal",
    "Associate",
    "Specialist",
    "Coordinator",
    "Officer",
    "Analyst",
    "Consultant",
    "Engineer",
    "Developer",
    "Architect",
    "Designer",
    "Researcher",
    "Scientist",
    "Producer",
    "Planner",
    "Strategist",
)
_SORTED_TITLES: tuple[str, ...] = tuple(sorted(_TITLE_DICT, key=len, reverse=True))

_COMPANY_SUFFIXES: tuple[str, ...] = (
    "(주)",
    "주식회사",
    "Inc.",
    "Inc",
    "Co., Ltd.",
    "Co.,Ltd.",
    "Co.,Ltd",
    "Co. Ltd.",
    "Co. Ltd",
    "Corp.",
    "Corp",
    "LLC",
    "Ltd.",
    "Ltd",
)


def _extract_emails(text: str) -> list[str]:
    return [_strip_email_label_prefix(e) for e in _EMAIL_RE.findall(text)]


def _strip_email_label_prefix(email: str) -> str:
    first_dot = email.find(".")
    if first_dot == -1:
        return email
    head = email[:first_dot]
    if head in _EMAIL_LABEL_PREFIXES or head.lower() in {p.lower() for p in _EMAIL_LABEL_PREFIXES}:
        return email[first_dot + 1 :]
    return email


def _extract_phones(text: str) -> list[str]:
    return _PHONE_RE.findall(text)


def _pick_phone(phones: list[str]) -> str | None:
    def _digits(p: str) -> str:
        return re.sub(r"\D", "", p)

    for p in phones:
        if _digits(p).startswith("010"):
            return p
    for p in phones:
        if _digits(p).startswith("02"):
            return p
    return phones[0] if phones else None


_KOREAN_TITLE_PREFIX = r"(?:수석|책임|선임|주임|총괄|부총괄|고급)\s+"
_ENGLISH_TITLE_PREFIX = r"(?:Senior|Junior|Lead|Head|Chief|Vice|Managing|Solution|Software|Co)\s+"


def _match_title(text: str) -> str | None:
    for line in text.splitlines():
        line_stripped = line.strip()
        if not line_stripped or any(ch.isdigit() for ch in line_stripped):
            continue
        for title in _SORTED_TITLES:
            if title.isascii():
                continue
            pattern = re.compile(rf"(?:{_KOREAN_TITLE_PREFIX})?{re.escape(title)}")
            m = pattern.search(line_stripped)
            if m:
                return m.group(0).strip()

    for title in _SORTED_TITLES:
        if title.isascii():
            pattern = re.compile(
                rf"(?<![A-Za-z])(?:{_ENGLISH_TITLE_PREFIX})?{re.escape(title)}(?![A-Za-z])",
                re.IGNORECASE,
            )
            m = pattern.search(text)
            if m:
                return m.group(0).strip()
    return None


def _has_company_suffix(text: str) -> bool:
    return any(suffix in text for suffix in _COMPANY_SUFFIXES)


_GENERIC_DOMAIN_WORDS: frozenset[str] = frozenset(
    {
        "mail",
        "email",
        "www",
        "smtp",
        "com",
        "kr",
        "net",
        "org",
        "io",
        "co",
        "gov",
        "edu",
        "gmail",
        "naver",
        "daum",
        "hanmail",
        "nate",
        "kakao",
        "outlook",
        "hotmail",
        "yahoo",
        "icloud",
        "live",
        "msn",
    }
)


def _email_domain_word(email: str) -> str | None:
    at = email.rfind("@")
    if at < 0:
        return None
    parts = email[at + 1 :].split(".")
    company_parts = [p for p in parts if p.lower() not in _GENERIC_DOMAIN_WORDS]
    return company_parts[0] if company_parts else None


def _normalize_alnum(s: str) -> str:
    return re.sub(r"[^a-z0-9가-힣]+", "", s.lower())


def _fuzzy_company_match(domain_word: str, text: str) -> bool:
    a = _normalize_alnum(domain_word)
    b = _normalize_alnum(text)
    if not a or not b:
        return False
    return a in b or b in a


_SUSPECT_CONFIDENCE = 0.85


def _is_suspect_korean(b: OcrBox) -> bool:
    return b.confidence < _SUSPECT_CONFIDENCE and _is_mostly_korean(b.text)


def _filter_watermarks(candidates: list[OcrBox], all_boxes: list[OcrBox]) -> list[OcrBox]:
    if len(all_boxes) < 4 or not candidates:
        return candidates
    fonts = sorted((b.font_height for b in all_boxes), reverse=True)
    median = fonts[len(fonts) // 2]
    biggest = fonts[0]
    if biggest > median * 2.5 and biggest > fonts[1] * 1.5:
        return [b for b in candidates if b.font_height < biggest]
    return candidates


def _company_score(b: OcrBox, card_height: float) -> float:
    top_y = b.box[0][1]
    position_weight = 1.0 - 0.5 * min(top_y / card_height, 1.0)
    return b.font_height * position_weight


def _pick_company(boxes: list[OcrBox], *, emails: list[str] | None = None) -> str | None:
    if not boxes:
        return None
    sorted_by_font = sorted(boxes, key=lambda b: b.font_height, reverse=True)
    for b in sorted_by_font:
        if _has_company_suffix(b.text):
            return b.text

    candidates = [b for b in sorted_by_font if not _is_company_excluded(b.text) and not _is_suspect_korean(b)]
    candidates = _filter_watermarks(candidates, boxes)

    domain_word = _email_domain_word(emails[0]) if emails else None

    if candidates:
        card_height = max((b.box[2][1] for b in boxes), default=1.0) or 1.0
        candidates.sort(key=lambda b: _company_score(b, card_height), reverse=True)
        if domain_word:
            for b in candidates:
                if _fuzzy_company_match(domain_word, b.text):
                    return b.text
        return candidates[0].text

    return domain_word


def _is_company_excluded(text: str) -> bool:
    if _EMAIL_RE.search(text) or _PHONE_RE.search(text):
        return True
    if _LABEL_PREFIX_RE.match(text):
        return True
    if _DOMAIN_TAIL_RE.search(text):
        return True
    if 2 <= len(text) <= 5 and _is_mostly_korean(text):
        return True
    if _match_title(text):
        return True
    tokens = text.split()
    if len(tokens) >= 2:
        has_korean_name = any(2 <= len(t) <= 5 and _is_mostly_korean(t) for t in tokens)
        has_title = any(_match_title(t) for t in tokens)
        if has_korean_name and has_title:
            return True
    return False


def _is_mostly_korean(text: str) -> bool:
    letters = [c for c in text if c.isalpha() or "가" <= c <= "힣"]
    if not letters:
        return False
    korean = sum(1 for c in letters if "가" <= c <= "힣")
    return korean / len(letters) > 0.5


def _pick_name(boxes: list[OcrBox], *, exclude_texts: set[str]) -> str | None:

    def _is_email_or_phone(text: str) -> bool:
        return bool(_EMAIL_RE.search(text) or _PHONE_RE.search(text))

    candidates = [
        b
        for b in boxes
        if b.text not in exclude_texts and not _is_email_or_phone(b.text) and not _has_company_suffix(b.text)
    ]
    if not candidates:
        return None

    korean_pieces = [
        b for b in candidates if 1 <= len(b.text) <= 3 and _is_mostly_korean(b.text) and not _match_title(b.text)
    ]
    if korean_pieces:
        sorted_by_font = sorted(korean_pieces, key=lambda b: b.font_height, reverse=True)
        biggest = sorted_by_font[0]
        related = [b for b in sorted_by_font if abs(b.font_height - biggest.font_height) <= biggest.font_height * 0.3]
        related.sort(key=lambda b: (b.box[0][1], b.box[0][0]))
        combined = "".join(b.text for b in related)
        if 2 <= len(combined) <= 5:
            return combined

    for b in candidates:
        for token in b.text.split():
            if 2 <= len(token) <= 5 and _is_mostly_korean(token) and not _match_title(token):
                return token

    korean_chunk_re = re.compile(r"[가-힣]{2,5}")
    for b in candidates:
        for chunk in korean_chunk_re.findall(b.text):
            if not _match_title(chunk):
                return chunk

    return None


def _is_ambiguous(
    *,
    name: str | None,
    company: str | None,
    title: str | None,
    boxes: list[OcrBox],
) -> bool:
    if not name or not company:
        return True
    if _has_company_suffix(company):
        return False
    company_box = next((b for b in boxes if b.text == company), None)
    if company_box is None:
        return True
    return _is_suspect_korean(company_box)


def _cache_key(*, tenant_id: int, raw_text: str) -> str:
    digest = hashlib.sha256(raw_text.encode("utf-8")).hexdigest()
    return f"ocr:bc:v2:{tenant_id}:{digest}"


_LLM_BOX_LIMIT = 8


async def _classify_with_llm(
    boxes: list[OcrBox],
    openai: OpenAIClient,
    model: str,
) -> BusinessCardClassification:
    relevant = [b for b in boxes if not (_EMAIL_RE.search(b.text) or _PHONE_RE.search(b.text))]
    relevant.sort(key=lambda b: b.font_height, reverse=True)
    lines: list[str] = []
    for b in relevant[:_LLM_BOX_LIMIT]:
        x, y = b.box[0]
        suspect = " suspect" if _is_suspect_korean(b) else ""
        lines.append(f"[h={b.font_height:.0f} x={x:.0f} y={y:.0f}{suspect}] {b.text}")
    box_dump = "\n".join(lines)
    system_prompt = (
        "명함 OCR 박스에서 name/company/title 분류. "
        "입력 형식: `[h=폰트높이 x=좌상단X y=좌상단Y (suspect)] 텍스트`. "
        "`suspect` 마커는 OCR confidence 가 낮은 한글 박스 — "
        "글자가 깨졌을 가능성 높음, 무시하거나 다른 컨텍스트로 정정. "
        "명함 레이아웃 특성: "
        "(1) 회사명은 보통 상단·좌상단에 위치하거나 로고와 함께 배치된다. "
        "폰트가 항상 가장 크지 않으며 워터마크/배경 장식이 본문보다 큰 폰트로 잡힐 수 있으니 "
        "위치·맥락으로 판단한다. "
        "(2) 이름은 명함 중앙 또는 상단에 위치하며 직책과 인접(같은 y 근처)한다. "
        "폰트는 본문 중 큰 편이지만 회사명보다 작을 수 있다. "
        "(3) 직책은 이름 바로 아래 또는 옆에 작은 폰트로 배치된다. "
        "(4) 주소·연락처는 명함 하단에 모인다(상대적 큰 y). "
        "(5) suspect 박스 또는 깨진 한글은 이메일 도메인(`@xxx.com`), "
        "영문 로고 박스, 주소의 빌딩명/회사명으로 정정. "
        "확실하지 않으면 null."
    )
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": box_dump},
    ]
    result = await openai.chat_structured(messages, response_model=BusinessCardClassification, model=model)
    return cast(BusinessCardClassification, result)


_BUSINESS_CARD_S3_PREFIX = "business-cards/"

_CACHE_TTL_SECONDS = 86400


@router.post("/business-card")
async def recognize_business_card(
    body: BusinessCardOcrRequest,
    tenant_id: int = Depends(get_tenant_id),
    s3: S3Client = Depends(get_s3_client),
    ocr: OcrClient = Depends(get_ocr_client),
    openai: OpenAIClient = Depends(get_openai_client),
    redis: Redis = Depends(get_redis),
    settings: Settings = Depends(get_settings),
) -> ApiResponse[BusinessCardOcrResponse]:
    if not body.s3_key.startswith(_BUSINESS_CARD_S3_PREFIX):
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            f"s3_key 는 '{_BUSINESS_CARD_S3_PREFIX}' 프리픽스여야 합니다",
        )

    image_bytes = await s3.get_object(body.s3_key)
    boxes = await ocr.recognize(image_bytes)
    raw_text = "\n".join(b.text for b in boxes)

    cache_key = _cache_key(tenant_id=tenant_id, raw_text=raw_text)
    cached = await redis.get(cache_key)
    if cached is not None:
        cached_response = BusinessCardOcrResponse.model_validate_json(cached)
        return ApiResponse.ok(cached_response)

    emails = _extract_emails(raw_text)
    phones = _extract_phones(raw_text)
    title = _match_title(raw_text)
    company = _pick_company(boxes, emails=emails)

    excluded: set[str] = {t for t in (company, title, *emails, *phones) if t}
    name = _pick_name(boxes, exclude_texts=excluded)

    if _is_ambiguous(name=name, company=company, title=title, boxes=boxes):
        try:
            classification = await _classify_with_llm(
                boxes,
                openai,
                settings.OPENAI_OCR_MODEL,
            )
            name = classification.name or name
            company = classification.company or company
            title = classification.title or title
        except BusinessException:
            pass

    response = BusinessCardOcrResponse(
        name=name,
        company=company,
        title=title,
        phone=_pick_phone(phones),
        email=emails[0] if emails else None,
    )
    await redis.set(cache_key, response.model_dump_json(), ex=_CACHE_TTL_SECONDS)
    return ApiResponse.ok(response)
