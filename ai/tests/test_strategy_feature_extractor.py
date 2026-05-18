"""Unit tests for strategy feature_extractor mock parser."""
import pytest

from app.strategy.feature_extractor import extract_features_mock, PRIORITY_IDS


class TestMockParser:
    def test_extracts_korean_stage(self):
        result = extract_features_mock("디스커버리 단계의 고객입니다")
        assert result.get("FEAT_007") == "discovery"

    def test_extracts_poc_stage(self):
        result = extract_features_mock("poc 진행 중인 건입니다")
        assert result.get("FEAT_007") == "poc"

    def test_extracts_industry(self):
        result = extract_features_mock("금융 업종의 대기업")
        assert result.get("FEAT_002") == "finance"

    def test_extracts_geography_korea(self):
        result = extract_features_mock("한국 기업과 미팅 예정")
        assert result.get("FEAT_003") == "korea"

    def test_extracts_company_size(self):
        result = extract_features_mock("대기업 고객사")
        assert result.get("FEAT_001") == "enterprise"

    def test_extracts_champion_missing(self):
        result = extract_features_mock("챔피언이 아직 없는 상황")
        assert result.get("FEAT_011") is False

    def test_extracts_champion_present(self):
        result = extract_features_mock("챔피언 확보 완료")
        assert result.get("FEAT_011") is True

    def test_extracts_ciso_blocking(self):
        result = extract_features_mock("CISO가 차단 중")
        assert result.get("FEAT_015") == "blocking"

    def test_extracts_compliance_multi_select(self):
        result = extract_features_mock("k-isms 인증 필요하고 soc2도 필요합니다")
        assert "k_isms" in result.get("FEAT_056", [])
        assert "soc2" in result.get("FEAT_056", [])

    def test_extracts_objections_multi_select(self):
        result = extract_features_mock("가격이 비싸다고 하고 보안 우려도 있습니다")
        assert "price" in result.get("FEAT_059", [])
        assert "security_concern" in result.get("FEAT_059", [])

    def test_extracts_poc_status(self):
        result = extract_features_mock("poc 스코핑 시작합니다")
        assert result.get("FEAT_049") == "scoping"

    def test_extracts_korean_market_features(self):
        result = extract_features_mock("한국 레퍼런스 없는 상황에서 SI 파트너 검토 중")
        assert result.get("FEAT_075") is False
        assert result.get("FEAT_077") == "considering"

    def test_comprehensive_scenario(self):
        text = "한국 대형 보험사 PoC 스코핑 중. CISO가 K-ISMS 자료 요청. 챔피언 확보됨."
        result = extract_features_mock(text)
        assert result.get("FEAT_003") == "korea"
        assert result.get("FEAT_002") == "finance"
        assert result.get("FEAT_007") == "poc"
        assert result.get("FEAT_049") == "scoping"
        assert result.get("FEAT_011") is True
        assert "k_isms" in result.get("FEAT_056", [])

    def test_empty_text_returns_empty(self):
        result = extract_features_mock("")
        assert result == {}

    def test_unrelated_text_returns_empty(self):
        result = extract_features_mock("오늘 날씨가 좋습니다")
        assert len(result) == 0


class TestPriorityFeatures:
    def test_priority_ids_count(self):
        assert len(PRIORITY_IDS) == 32

    def test_tier1_included(self):
        assert "FEAT_007" in PRIORITY_IDS
        assert "FEAT_059" in PRIORITY_IDS
        assert "FEAT_011" in PRIORITY_IDS

    def test_korean_boost_included(self):
        assert "FEAT_003" in PRIORITY_IDS
        assert "FEAT_075" in PRIORITY_IDS
        assert "FEAT_080" in PRIORITY_IDS
