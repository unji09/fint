"""Unit tests for strategy weights scoring."""
import pytest

from app.strategy.weights import compute_scores


class TestStageWeights:
    def test_awareness_boosts_prospecting(self):
        features = {"FEAT_007": "awareness"}
        scores = compute_scores(features)
        assert scores["ACT_001"] > 0
        assert scores["ACT_002"] > 0
        assert scores.get("ACT_077", 0) < 0

    def test_discovery_boosts_discovery_actions(self):
        features = {"FEAT_007": "discovery"}
        scores = compute_scores(features)
        assert scores["ACT_011"] > 0
        assert scores["ACT_014"] > 0

    def test_negotiation_boosts_negotiation_actions(self):
        features = {"FEAT_007": "negotiation"}
        scores = compute_scores(features)
        assert scores["ACT_070"] > 0
        assert scores["ACT_077"] > 0


class TestPocStatus:
    def test_scoping_boosts_poc_setup(self):
        features = {"FEAT_007": "poc", "FEAT_049": "scoping"}
        scores = compute_scores(features)
        assert scores["ACT_037"] >= 1.0

    def test_running_without_biz_stakeholder(self):
        features = {"FEAT_007": "poc", "FEAT_049": "running", "FEAT_051": False}
        scores = compute_scores(features)
        assert scores["ACT_041"] > 0.8


class TestSecurityCompliance:
    def test_k_isms_needed_without_docs(self):
        features = {
            "FEAT_007": "evaluation",
            "FEAT_056": ["k_isms"],
            "FEAT_055": [],
        }
        scores = compute_scores(features)
        assert scores["ACT_095"] >= 0.9

    def test_ciso_blocking(self):
        features = {"FEAT_007": "poc", "FEAT_015": "blocking"}
        scores = compute_scores(features)
        assert scores["ACT_055"] >= 0.9


class TestStakeholderGaps:
    def test_no_champion_in_discovery(self):
        features = {"FEAT_007": "discovery", "FEAT_011": False}
        scores = compute_scores(features)
        assert scores["ACT_045"] >= 0.7

    def test_no_eb_in_negotiation(self):
        features = {"FEAT_007": "negotiation", "FEAT_013": False}
        scores = compute_scores(features)
        assert scores["ACT_024"] >= 0.7


class TestObjections:
    def test_price_objection(self):
        features = {"FEAT_007": "negotiation", "FEAT_059": ["price"]}
        scores = compute_scores(features)
        assert scores["ACT_062"] > 0.8
        assert scores["ACT_070"] > 0.8


class TestStaleDeal:
    def test_30_days_in_evaluation(self):
        features = {"FEAT_007": "evaluation", "FEAT_008": 35}
        scores = compute_scores(features)
        assert scores["ACT_079"] > 0.5

    def test_14_days_no_contact(self):
        features = {"FEAT_007": "discovery", "FEAT_009": 20}
        scores = compute_scores(features)
        assert scores["ACT_009"] > 0.5


class TestKoreanMarket:
    def test_korea_enterprise_no_local_ref(self):
        features = {
            "FEAT_003": "korea",
            "FEAT_001": "enterprise",
            "FEAT_007": "evaluation",
            "FEAT_075": False,
            "FEAT_055": [],
            "FEAT_056": [],
        }
        scores = compute_scores(features)
        assert scores["ACT_094"] >= 0.9

    def test_si_partner_considering(self):
        features = {
            "FEAT_003": "korea",
            "FEAT_007": "evaluation",
            "FEAT_077": "considering",
            "FEAT_055": [],
            "FEAT_056": [],
        }
        scores = compute_scores(features)
        assert scores["ACT_096"] >= 0.9
