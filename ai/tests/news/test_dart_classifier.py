"""DART 공시 분류기 단위 테스트."""
import pytest

from app.news.dart_classifier import (
    DisclosureClassification,
    analyze,
    build_signal_summary,
    classify,
    extract_key_values,
)


class TestClassify:
    """report_nm 패턴 매칭 분류."""

    @pytest.mark.parametrize("report_nm", [
        "사업보고서 (2025.12)",
        "반기보고서 (2026.06)",
        "분기보고서 (2026.03)",
    ])
    def test_periodic_report_p0(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "periodic_report"
        assert r.priority == 0
        assert "earnings_commentary" in r.trigger_event_types

    @pytest.mark.parametrize("report_nm", [
        "타법인주식및출자증권취득결정",
        "합병결정",
        "분할결정",
        "물적분할결정",
        "영업양수도결정",
    ])
    def test_ma_p0(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "ma"
        assert r.priority == 0
        assert "ma_announcement" in r.trigger_event_types

    @pytest.mark.parametrize("report_nm", [
        "대표이사(대표집행임원)변경",
        "대표이사변경",
    ])
    def test_executive_change_p0(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "executive_change"
        assert r.priority == 0
        assert "leadership_change" in r.trigger_event_types
        assert "new_executive_hire" in r.trigger_event_types

    @pytest.mark.parametrize("report_nm", [
        "유상증자결정",
        "전환사채권발행결정",
        "신주인수권부사채권발행결정",
        "무상증자결정",
    ])
    def test_fundraising_p1(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "fundraising"
        assert r.priority == 1
        assert "funding_round" in r.trigger_event_types

    @pytest.mark.parametrize("report_nm", [
        "단일판매ㆍ공급계약체결",
        "단일판매ㆍ공급계약해지",
    ])
    def test_major_contract_p1(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "major_contract"
        assert r.priority == 1

    @pytest.mark.parametrize("report_nm", [
        "공정공시",
        "자율공시",
        "주요사항보고서(자율공시)",
    ])
    def test_voluntary_disclosure_p1(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "voluntary_disclosure"
        assert r.priority == 1

    @pytest.mark.parametrize("report_nm", [
        "주식등의대량보유상황보고서",
        "최대주주변경",
    ])
    def test_ownership_change_p2(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "ownership_change"
        assert r.priority == 2
        assert "leadership_change" in r.trigger_event_types

    @pytest.mark.parametrize("report_nm", [
        "소송등의판결(중재판정)",
        "행정처분ㆍ제재",
    ])
    def test_litigation_p2(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "litigation"
        assert r.priority == 2

    @pytest.mark.parametrize("report_nm", [
        "임원ㆍ주요주주특정증권등소유상황보고서",
        "사외이사의선임ㆍ해임",
        "감사의선임ㆍ해임",
    ])
    def test_officer_change_p2(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "officer_change"
        assert r.priority == 2
        assert "new_executive_hire" in r.trigger_event_types

    @pytest.mark.parametrize("report_nm", [
        "자기주식취득결정",
        "배당결정",
        "주주총회소집결의",
    ])
    def test_other_financial_p3(self, report_nm: str):
        r = classify(report_nm)
        assert r.category == "other_financial"
        assert r.priority == 3

    def test_unknown_falls_back_to_other(self):
        r = classify("알수없는보고서제목")
        assert r.category == "other"
        assert r.priority == 3

    def test_correction_prefix_stripped(self):
        assert classify("[정정]사업보고서 (2025.12)").category == "periodic_report"

    def test_correction_prefix_기재정정(self):
        assert classify("[기재정정]분기보고서 (2026.03)").category == "periodic_report"

    def test_correction_prefix_첨부정정(self):
        assert classify("[첨부정정]합병결정").category == "ma"


class TestExtractKeyValues:
    """공시 본문에서 핵심 값 추출."""

    def test_periodic_report_financials(self):
        content = (
            "III. 재무에 관한 사항 "
            "매출액 45,678,901 영업이익 12,345,678 "
            "당기순이익 8,765,432 "
            "IV. 임원 및 직원 등에 관한 사항 "
            "직원 수 120,000명"
        )
        r = extract_key_values("periodic_report", content)
        assert "매출액" in r
        assert "45,678,901" in r["매출액"]
        assert "영업이익" in r
        assert "당기순이익" in r
        assert "직원수" in r
        assert "120,000" in r["직원수"]

    def test_periodic_report_with_unit(self):
        content = "매출액 1,234백만원 영업이익 567억원"
        r = extract_key_values("periodic_report", content)
        assert r["매출액"] == "1,234백만원"
        assert r["영업이익"] == "567억원"

    def test_ma_target_and_amount(self):
        content = (
            "1. 발행회사 : ABC기술 주식회사 "
            "2. 취득주식수 : 1,000,000주 "
            "3. 취득금액 : 50,000,000,000원 "
            "4. 취득목적 : 경영참여"
        )
        r = extract_key_values("ma", content)
        assert "취득금액" in r
        assert "50,000,000,000" in r["취득금액"]
        assert "취득목적" in r
        assert "경영참여" in r["취득목적"]

    def test_executive_change_name_and_reason(self):
        content = (
            "1. 변경 구분 : 신규선임 "
            "2. 성명 : 홍길동 "
            "3. 변경사유 : 정기주주총회 선임 "
            "4. 변경일자 : 2026-03-25"
        )
        r = extract_key_values("executive_change", content)
        assert r["성명"] == "홍길동"
        assert "정기주주총회" in r["변경사유"]
        assert r["변경일자"] == "2026-03-25"

    def test_fundraising_amount_and_purpose(self):
        content = (
            "1. 신주의 종류와 수 : 보통주 5,000,000주 "
            "2. 발행가액 : 10,000원 "
            "3. 자금의 사용 목적 : 시설자금 및 운영자금"
        )
        r = extract_key_values("fundraising", content)
        assert "발행가액" in r
        assert "10,000" in r["발행가액"]
        assert "자금사용목적" in r
        assert "시설자금" in r["자금사용목적"]

    def test_major_contract_amount_and_counterpart(self):
        content = (
            "1. 계약상대방 : XYZ그룹 "
            "2. 계약금액 : 100,000,000,000원 "
            "3. 매출액 대비 : 15.2%"
        )
        r = extract_key_values("major_contract", content)
        assert "계약금액" in r
        assert "100,000,000,000" in r["계약금액"]
        assert "계약상대방" in r
        assert "XYZ그룹" in r["계약상대방"]
        assert r["매출액대비"] == "15.2"

    def test_voluntary_disclosure_detects_keywords(self):
        content = "당사는 AI 분야 신규 사업 진출을 결정하였으며, 대규모 채용을 계획하고 있습니다."
        r = extract_key_values("voluntary_disclosure", content)
        assert "감지키워드" in r
        assert "신규사업" in r["감지키워드"]
        assert "채용확대" in r["감지키워드"]

    def test_voluntary_disclosure_no_keywords(self):
        content = "일반적인 공시 내용입니다."
        r = extract_key_values("voluntary_disclosure", content)
        assert r == {}

    def test_voluntary_disclosure_with_financials(self):
        """공정공시 실적 공시 — 키워드 없어도 재무 수치 추출."""
        content = "매출액 45,678백만원 영업이익 12,345백만원 당기순이익 8,000백만원"
        r = extract_key_values("voluntary_disclosure", content)
        assert r["매출액"] == "45,678백만원"
        assert r["영업이익"] == "12,345백만원"
        assert r["당기순이익"] == "8,000백만원"

    def test_voluntary_disclosure_keywords_and_financials_together(self):
        """키워드와 재무 수치가 동시에 존재하는 경우."""
        content = "신규 사업 진출. 예상 매출액 30,000억원"
        r = extract_key_values("voluntary_disclosure", content)
        assert "감지키워드" in r
        assert "신규사업" in r["감지키워드"]
        assert r["매출액"] == "30,000억원"

    # ── officer_change (임원변동) ──

    def test_officer_change_appointment(self):
        """사외이사 선임 공시."""
        content = (
            "1. 성명 : 김철수 "
            "2. 직위 : 사외이사 "
            "3. 선임ㆍ해임 구분 : 선임 "
            "4. 임기 : 2026.03.25 ~ 2029.03.24 "
            "5. 선해임사유 : 정기주주총회 결의"
        )
        r = extract_key_values("officer_change", content)
        assert r["성명"] == "김철수"
        assert r["직위"] == "사외이사"
        assert "선임" in r["선해임구분"]
        assert "정기주주총회" in r["선해임사유"]

    def test_officer_change_dismissal(self):
        """감사 해임 공시."""
        content = (
            "1. 성명 : 박영희 "
            "2. 직위 : 감사 "
            "3. 선임ㆍ해임 구분 : 해임 "
            "4. 선해임사유 : 임기 만료"
        )
        r = extract_key_values("officer_change", content)
        assert r["성명"] == "박영희"
        assert r["직위"] == "감사"
        assert "해임" in r["선해임구분"]

    def test_officer_change_stock_report(self):
        """임원ㆍ주요주주 특정증권등 소유상황보고서."""
        content = (
            "보고자 : 이대표 "
            "관계 : 임원 "
            "변동주식수 500,000주 "
            "변동사유 : 장내매수"
        )
        r = extract_key_values("officer_change", content)
        assert r["보고자"] == "이대표"
        assert "500,000" in r["변동주식수"]
        assert "장내매수" in r["변동사유"]

    # ── ownership_change (지분변동) ──

    def test_ownership_change_full(self):
        """대량보유 보고서 — 보고자/보유주식등의수/보유비율만 추출."""
        content = (
            "보고자 : 한국투자공사 "
            "보유 주식 수 1,500,000 보유비율 : 5.3%"
        )
        r = extract_key_values("ownership_change", content)
        assert r["보고자"] == "한국투자공사"
        assert "1,500,000" in r["보유주식등의수"]
        assert r["보유비율"] == "5.3%"

    def test_ownership_change_no_extra_fields(self):
        """보유목적/변동일자 등 불필요 필드는 추출하지 않는다."""
        content = (
            "보고자 : 한국투자공사 "
            "보유 주식 수 1,500,000 보유비율 : 5.3% "
            "보유목적 : 경영참여 "
            "변동일자 : 2026-04-15"
        )
        r = extract_key_values("ownership_change", content)
        assert "보유목적" not in r
        assert "변동일자" not in r

    # ── litigation (소송/제재) ──

    def test_litigation_full(self):
        """소송 판결 — 상대방/일자 추출 포함."""
        content = (
            "소송 금액 50,000,000,000원 "
            "소송상대방 : (주)ABC테크 "
            "소송내용 : 특허권 침해 손해배상 청구 "
            "판결일자 : 2026-03-15"
        )
        r = extract_key_values("litigation", content)
        assert "50,000,000,000" in r["소송금액"]
        assert "ABC테크" in r["소송상대방"]
        assert "특허권 침해" in r["소송내용"]
        assert "2026-03-15" in r["판결일자"]

    def test_litigation_sanction(self):
        """행정처분/제재 — 제재기관 추출."""
        content = (
            "제재기관 : 금융감독원 "
            "처분내용 : 과징금 부과 "
            "처분금액 5,000,000,000원"
        )
        r = extract_key_values("litigation", content)
        assert r["제재기관"] == "금융감독원"
        assert "과징금" in r["소송내용"]
        assert "5,000,000,000" in r["소송금액"]

    # ── other_financial (기타재무) ──

    def test_other_financial_treasury_stock(self):
        """자기주식 취득 결정."""
        content = (
            "1. 취득예정주식수 : 500,000주 "
            "2. 취득예정금액 : 25,000,000,000원 "
            "3. 취득목적 : 주가 안정 "
            "4. 취득기간 : 2026.05.01 ~ 2026.07.31"
        )
        r = extract_key_values("other_financial", content)
        assert "500,000" in r["취득주식수"]
        assert "25,000,000,000" in r["취득금액"]
        assert "주가 안정" in r["취득목적"]

    def test_other_financial_dividend(self):
        """배당 결정."""
        content = (
            "1. 배당구분 : 결산배당 "
            "2. 주당배당금 : 1,500원 "
            "3. 배당총액 : 30,000,000,000원 "
            "4. 배당기준일 : 2025-12-31"
        )
        r = extract_key_values("other_financial", content)
        assert "1,500" in r["주당배당금"]
        assert "30,000,000,000" in r["배당총액"]

    def test_other_financial_capital_reduction(self):
        """감자 결정."""
        content = (
            "1. 감자주식수 : 10,000,000주 "
            "2. 감자비율 : 50.0% "
            "3. 감자방법 : 무상감자"
        )
        r = extract_key_values("other_financial", content)
        assert "10,000,000" in r["감자주식수"]
        assert r["감자비율"] == "50.0"

    # ── HTML-stripped (no-colon) format ──
    # DART HTML tables become space-separated text after tag stripping

    def test_executive_change_no_colon(self):
        content = "1. 성명 홍길동 2. 변경사유 임기만료 3. 변경일자 2026-03-25"
        r = extract_key_values("executive_change", content)
        assert r["성명"] == "홍길동"
        assert "임기만료" in r["변경사유"]
        assert r["변경일자"] == "2026-03-25"

    def test_officer_change_no_colon(self):
        content = "성명 김철수 직위 사외이사 보고자 삼성전자 변동주식수 500,000"
        r = extract_key_values("officer_change", content)
        assert r["성명"] == "김철수"
        assert r["직위"] == "사외이사"
        assert r["보고자"] == "삼성전자"
        assert "500,000" in r["변동주식수"]

    def test_officer_change_name_with_parenthetical(self):
        content = "성명(명칭) 김철수 직위 감사"
        r = extract_key_values("officer_change", content)
        assert r["성명"] == "김철수"

    def test_ownership_change_no_colon(self):
        content = (
            "보고자 한국투자공사 "
            "소유 주식 수 1,500,000 보유 비율 5.3%"
        )
        r = extract_key_values("ownership_change", content)
        assert r["보고자"] == "한국투자공사"
        assert "1,500,000" in r["보유주식등의수"]
        assert r["보유비율"] == "5.3%"

    def test_litigation_no_colon(self):
        content = "제재기관 금융감독원 판결일자 2026-03-15 소송 금액 5,000,000,000원"
        r = extract_key_values("litigation", content)
        assert r["제재기관"] == "금융감독원"
        assert "2026-03-15" in r["판결일자"]
        assert "5,000,000,000" in r["소송금액"]

    def test_ma_no_colon(self):
        content = "취득금액 50,000,000,000원 매출액 1,000,000백만원"
        r = extract_key_values("ma", content)
        assert "50,000,000,000" in r["취득금액"]
        assert "1,000,000백만원" in r["매출액"]

    def test_empty_content_returns_empty(self):
        assert extract_key_values("periodic_report", "") == {}

    def test_none_content_returns_empty(self):
        assert extract_key_values("periodic_report", None) == {}

    def test_no_matching_patterns_returns_empty(self):
        assert extract_key_values("periodic_report", "관련 없는 텍스트") == {}

    def test_unknown_category_extracts_common_financials(self):
        """카테고리별 패턴이 없어도 공통 재무 수치는 추출."""
        r = extract_key_values("unknown_cat", "매출액 1,000")
        assert r["매출액"] == "1,000"


class TestBuildSignalSummary:
    """시그널 요약 생성."""

    def test_with_extracted_values(self):
        cls = DisclosureClassification("periodic_report", 0, "정기보고서", ("earnings_commentary",))
        r = build_signal_summary(cls, "분기보고서 (2026.03)", {"매출액": "45,678,901", "영업이익": "12,345,678"})
        assert "[정기보고서]" in r
        assert "분기보고서 (2026.03)" in r
        assert "매출액: 45,678,901" in r
        assert "영업이익: 12,345,678" in r

    def test_without_extracted_values(self):
        cls = DisclosureClassification("other", 3, "기타", ())
        r = build_signal_summary(cls, "알수없는보고서", {})
        assert "[기타]" in r
        assert "알수없는보고서" in r
        assert "|" not in r

    def test_ma_summary_format(self):
        cls = DisclosureClassification("ma", 0, "M&A/합병/분할", ("ma_announcement",))
        r = build_signal_summary(cls, "합병결정", {"취득금액": "500억원"})
        assert "M&A" in r
        assert "취득금액: 500억원" in r


class TestAnalyze:
    """analyze() 통합 테스트."""

    def test_periodic_report_full_pipeline(self):
        content = "매출액 10,000백만원 영업이익 2,000백만원"
        r = analyze("분기보고서 (2026.03)", content)
        assert r.classification.category == "periodic_report"
        assert r.classification.priority == 0
        assert r.trigger_event_types == ["earnings_commentary"]
        assert "매출액" in r.extracted_values
        assert "[정기보고서]" in r.signal_summary
        assert "10,000백만원" in r.signal_summary

    def test_no_content_still_classifies(self):
        r = analyze("합병결정", None)
        assert r.classification.category == "ma"
        assert r.extracted_values == {}
        assert "M&A" in r.signal_summary

    def test_correction_prefix_handled(self):
        r = analyze("[정정]유상증자결정", "발행가액 : 5,000원")
        assert r.classification.category == "fundraising"
        assert "발행가액" in r.extracted_values

    def test_voluntary_earnings_disclosure(self):
        """연결재무제표기준영업(잠정)실적(공정공시) 같은 실적 공정공시."""
        content = "매출액 45,678백만원 영업이익 12,345백만원"
        r = analyze("연결재무제표기준영업(잠정)실적(공정공시)", content)
        assert r.classification.category == "voluntary_disclosure"
        assert r.extracted_values["매출액"] == "45,678백만원"
        assert r.extracted_values["영업이익"] == "12,345백만원"
        assert "45,678백만원" in r.signal_summary

    def test_officer_change_flr_nm_fallback(self):
        """특정증권 보고서 — 보고자 '본인'이면 flr_nm 대체, 성명 제거."""
        content = "성명 한 보고자 본인은 변동주식수 100,000"
        r = analyze(
            "임원ㆍ주요주주특정증권등소유상황보고서",
            content,
            flr_nm="임태섭",
        )
        assert r.classification.category == "officer_change"
        assert r.extracted_values["보고자"] == "임태섭"
        assert "성명" not in r.extracted_values
        assert "임태섭" in r.signal_summary
        assert "성명" not in r.signal_summary

    def test_officer_change_flr_nm_not_needed(self):
        """특정증권 보고서 — 보고자 정상 추출, 성명 제거."""
        content = "성명 김철수 보고자 이대표 변동주식수 500,000"
        r = analyze(
            "임원ㆍ주요주주특정증권등소유상황보고서",
            content,
            flr_nm="삼성전자",
        )
        assert r.extracted_values["보고자"] == "이대표"
        assert "성명" not in r.extracted_values

    def test_officer_change_flr_nm_when_no_reporter(self):
        """특정증권 보고서 — 보고자 없으면 flr_nm 사용, 성명 제거."""
        content = "성명 김철수 직위 사외이사"
        r = analyze(
            "임원ㆍ주요주주특정증권등소유상황보고서",
            content,
            flr_nm="임태섭",
        )
        assert r.extracted_values["보고자"] == "임태섭"
        assert "성명" not in r.extracted_values

    def test_officer_change_keeps_name_for_appointment(self):
        """사외이사 선임 — 성명은 유지한다."""
        content = "성명 : 김철수 직위 : 사외이사"
        r = analyze("사외이사의선임ㆍ해임", content)
        assert r.extracted_values["성명"] == "김철수"