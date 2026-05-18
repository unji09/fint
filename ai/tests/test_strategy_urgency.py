"""Unit tests for strategy urgency module."""
import pytest

from app.strategy.urgency import evaluate_urgency


class TestEvaluateUrgency:
    def test_default_is_normal(self):
        result = evaluate_urgency({}, {"category": "Discovery"})
        assert result["level"] == "보통"
        assert result["n_stars"] >= 1

    def test_renewal_expired_is_urgent(self):
        features = {"FEAT_073": -5}
        result = evaluate_urgency(features, {"category": "Expansion & Renewal"})
        assert result["level"] == "긴급"
        assert "갱신 만료됨" in result["reasons"]

    def test_renewal_30_days(self):
        features = {"FEAT_073": 25}
        result = evaluate_urgency(features, {"category": "Expansion & Renewal"})
        assert result["score"] >= 4
        assert any("25일" in r for r in result["reasons"])

    def test_stale_deal_60_days(self):
        features = {"FEAT_008": 65}
        result = evaluate_urgency(features, {"category": "Discovery"})
        assert "stale" in " ".join(result["reasons"])
        assert result["score"] >= 3

    def test_champion_weakening(self):
        features = {"FEAT_012": "weakening"}
        result = evaluate_urgency(features, {"category": "Champion Building"})
        assert "챔피언 약화 중" in result["reasons"]

    def test_ciso_blocking(self):
        features = {"FEAT_015": "blocking"}
        result = evaluate_urgency(features, {"category": "Security"})
        assert "CISO 차단 상태" in result["reasons"]

    def test_multiple_objections(self):
        features = {"FEAT_059": ["price", "security_concern"]}
        result = evaluate_urgency(features, {"category": "Objection Handling"})
        assert any("이의제기" in r for r in result["reasons"])

    def test_closing_action_in_negotiation(self):
        features = {"FEAT_007": "negotiation"}
        result = evaluate_urgency(features, {"category": "Closing & MAP"})
        assert result["score"] >= 2

    def test_adoption_at_risk(self):
        features = {"FEAT_072": "at_risk"}
        result = evaluate_urgency(features, {"category": "Expansion & Renewal"})
        assert "어댑션 위험" in result["reasons"]

    def test_stars_range(self):
        result = evaluate_urgency({}, {"category": "Discovery"})
        assert 1 <= result["n_stars"] <= 5
