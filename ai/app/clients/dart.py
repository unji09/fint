"""DART 전자공시 OpenAPI 클라이언트."""
from __future__ import annotations

import io
import logging
import re
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from datetime import datetime, timedelta
from html.parser import HTMLParser

import httpx

logger = logging.getLogger(__name__)

_CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml"
_LIST_URL = "https://opendart.fss.or.kr/api/list.json"
_DOC_URL = "https://opendart.fss.or.kr/api/document.xml"
_COMPANY_URL = "https://opendart.fss.or.kr/api/company.json"
_MAX_CONTENT_LEN = 50_000


class _HTMLTextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._texts: list[str] = []
        self._skip = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in ("script", "style"):
            self._skip = True

    def handle_endtag(self, tag: str) -> None:
        if tag in ("script", "style"):
            self._skip = False

    def handle_data(self, data: str) -> None:
        if not self._skip:
            self._texts.append(data)

    def get_text(self) -> str:
        return " ".join(self._texts)


def _extract_text_from_html(html: str) -> str:
    extractor = _HTMLTextExtractor()
    extractor.feed(html)
    text = extractor.get_text()
    return re.sub(r"\s+", " ", text).strip()


@dataclass(frozen=True, slots=True)
class CorpInfo:
    corp_code: str
    corp_name: str
    stock_code: str


@dataclass(frozen=True, slots=True)
class CompanyInfo:
    corp_code: str
    corp_name: str
    ceo_nm: str
    bizr_no: str
    induty_code: str
    adres: str
    phn_no: str
    est_dt: str


_EN_KO_MAP: dict[str, str] = {
    "SDS": "에스디에스",
    "CNS": "씨엔에스",
    "SK": "에스케이",
    "LG": "엘지",
    "KT": "케이티",
    "GS": "지에스",
    "CJ": "씨제이",
    "HD": "에이치디",
    "LS": "엘에스",
    "DL": "디엘",
    "OCI": "오씨아이",
}

_CORP_SUFFIXES = re.compile(
    r"[\(（]주[\)）]|주식회사|[\(（]유[\)）]|유한회사|[\(（]합[\)）]",
)


def _normalize_corp_name(name: str) -> str:
    return _CORP_SUFFIXES.sub("", name).strip()


def _transliterate_english(name: str) -> str:
    result = name
    for en, ko in sorted(_EN_KO_MAP.items(), key=lambda x: -len(x[0])):
        result = re.sub(re.escape(en), ko, result, flags=re.IGNORECASE)
    return result.replace(" ", "")


@dataclass(frozen=True, slots=True)
class DartDisclosureItem:
    corp_code: str
    corp_name: str
    stock_code: str
    corp_cls: str
    report_nm: str
    rcept_no: str
    flr_nm: str
    rcept_dt: str
    rm: str


def _clean(val: str | None) -> str:
    return (val or "").strip()


class DartClient:
    def __init__(self, api_key: str) -> None:
        self._api_key = api_key
        self._corp_map: dict[str, list[CorpInfo]] | None = None

    async def ensure_corp_codes(self) -> None:
        if self._corp_map is not None:
            return

        logger.info("Downloading DART corp code list...")
        async with httpx.AsyncClient(timeout=60.0) as client:
            resp = await client.get(
                _CORP_CODE_URL,
                params={"crtfc_key": self._api_key},
            )
            resp.raise_for_status()

        corp_map: dict[str, list[CorpInfo]] = {}
        with zipfile.ZipFile(io.BytesIO(resp.content)) as zf:
            xml_name = zf.namelist()[0]
            with zf.open(xml_name) as f:
                tree = ET.parse(f)

        for elem in tree.findall(".//list"):
            corp_code = _clean(elem.findtext("corp_code"))
            corp_name = _clean(elem.findtext("corp_name"))
            stock_code = _clean(elem.findtext("stock_code"))

            if not corp_code or not corp_name:
                continue

            info = CorpInfo(
                corp_code=corp_code,
                corp_name=corp_name,
                stock_code=stock_code,
            )
            corp_map.setdefault(corp_name, []).append(info)

        self._corp_map = corp_map
        logger.info("Loaded %d unique corp names from DART", len(corp_map))

    def find_corp(self, company_name: str) -> CorpInfo | None:
        if self._corp_map is None:
            return None

        matches = self._corp_map.get(company_name)
        if not matches:
            return None

        listed = [m for m in matches if m.stock_code]
        return listed[0] if listed else matches[0]

    def find_corp_fuzzy(self, company_name: str) -> list[CorpInfo]:
        if self._corp_map is None:
            return []

        # 1. exact
        exact = self.find_corp(company_name)
        if exact:
            return [exact]

        # 2. normalize
        normalized = _normalize_corp_name(company_name)
        if normalized != company_name:
            exact = self.find_corp(normalized)
            if exact:
                return [exact]

        # 3. transliterate
        transliterated = _transliterate_english(normalized)
        if transliterated != normalized:
            exact = self.find_corp(transliterated)
            if exact:
                return [exact]
            norm_trans = _normalize_corp_name(transliterated)
            if norm_trans != transliterated:
                exact = self.find_corp(norm_trans)
                if exact:
                    return [exact]

        # 4. corp_map scan fallback
        query = transliterated if transliterated != normalized else normalized
        if len(query) < 2:
            return []

        # 4a. DART 이름도 음역해서 exact 매칭 시도 (LG씨엔에스 → 엘지씨엔에스)
        for dart_name, infos in self._corp_map.items():
            dart_trans = _transliterate_english(_normalize_corp_name(dart_name))
            if dart_trans == query:
                listed = [i for i in infos if i.stock_code]
                return [listed[0] if listed else infos[0]]

        # 4b. contains scan
        _MAX_CANDIDATES = 5
        candidates: list[CorpInfo] = []
        for dart_name, infos in self._corp_map.items():
            dart_norm = _normalize_corp_name(dart_name)
            dart_trans = _transliterate_english(dart_norm)
            shorter, longer = (query, dart_trans) if len(query) <= len(dart_trans) else (dart_trans, query)
            if shorter not in longer:
                continue
            if len(shorter) / len(longer) < 0.6:
                continue
            listed = [i for i in infos if i.stock_code]
            candidates.append(listed[0] if listed else infos[0])
            if len(candidates) >= _MAX_CANDIDATES:
                break

        return candidates

    async def get_company_info(self, corp_code: str) -> CompanyInfo | None:
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(
                    _COMPANY_URL,
                    params={"crtfc_key": self._api_key, "corp_code": corp_code},
                )
                resp.raise_for_status()

            data = resp.json()
            if data.get("status") != "000":
                return None

            return CompanyInfo(
                corp_code=_clean(data.get("corp_code")),
                corp_name=_clean(data.get("corp_name")),
                ceo_nm=_clean(data.get("ceo_nm")),
                bizr_no=_clean(data.get("bizr_no")),
                induty_code=_clean(data.get("induty_code")),
                adres=_clean(data.get("adres")),
                phn_no=_clean(data.get("phn_no")),
                est_dt=_clean(data.get("est_dt")),
            )
        except Exception:
            logger.warning("Failed to fetch company info for corp_code=%s", corp_code, exc_info=True)
            return None

    async def fetch_document(self, rcept_no: str) -> str | None:
        """rcept_no로 공시 원문 ZIP을 다운로드하고 텍스트를 추출한다."""
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                resp = await client.get(
                    _DOC_URL,
                    params={"crtfc_key": self._api_key, "rcept_no": rcept_no},
                )
                resp.raise_for_status()

            content_type = resp.headers.get("content-type", "")
            if "xml" in content_type and "zip" not in content_type:
                return None

            with zipfile.ZipFile(io.BytesIO(resp.content)) as zf:
                texts: list[str] = []
                for name in sorted(zf.namelist()):
                    lower = name.lower()
                    if lower.endswith((".xml", ".html", ".htm")):
                        with zf.open(name) as f:
                            raw = f.read().decode("utf-8", errors="replace")
                            extracted = _extract_text_from_html(raw)
                            if extracted:
                                texts.append(extracted)

                if not texts:
                    return None

                full_text = "\n".join(texts)
                if len(full_text) > _MAX_CONTENT_LEN:
                    return full_text[:_MAX_CONTENT_LEN]
                return full_text

        except Exception:
            logger.warning(
                "Failed to fetch document for rcept_no=%s",
                rcept_no, exc_info=True,
            )
            return None

    async def list_disclosures(
        self,
        corp_code: str,
        *,
        bgn_de: str | None = None,
        end_de: str | None = None,
        page_count: int = 100,
    ) -> list[DartDisclosureItem]:
        if bgn_de is None:
            bgn_de = (datetime.now() - timedelta(days=7)).strftime("%Y%m%d")
        if end_de is None:
            end_de = datetime.now().strftime("%Y%m%d")

        params = {
            "crtfc_key": self._api_key,
            "corp_code": corp_code,
            "bgn_de": bgn_de,
            "end_de": end_de,
            "page_count": page_count,
        }

        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(_LIST_URL, params=params)
            resp.raise_for_status()

        data = resp.json()
        if data.get("status") != "000":
            return []

        items: list[DartDisclosureItem] = []
        for raw in data.get("list", []):
            try:
                stock = _clean(raw.get("stock_code"))
                corp_cls = _clean(raw.get("corp_cls"))
                items.append(
                    DartDisclosureItem(
                        corp_code=_clean(raw.get("corp_code")),
                        corp_name=_clean(raw.get("corp_name")),
                        stock_code=stock or "",
                        corp_cls=corp_cls if corp_cls in ("Y", "K", "N", "E") else "",
                        report_nm=_clean(raw.get("report_nm")),
                        rcept_no=_clean(raw.get("rcept_no")),
                        flr_nm=_clean(raw.get("flr_nm")),
                        rcept_dt=_clean(raw.get("rcept_dt")),
                        rm=_clean(raw.get("rm")),
                    )
                )
            except Exception:
                logger.warning("Failed to parse DART item: %s", raw, exc_info=True)

        return items