"""Unit tests for strategy recommendation engine."""
import pytest

from app.strategy.engine import recommend


class TestRecommend:
    def test_returns_top_n_results(self):
        features = {"FEAT_007": "discovery", "FEAT_011": False}
        results = recommend(features, top_n=3, min_score=0.3)
        assert len(results) <= 3
        assert len(results) > 0

    def test_results_sorted_by_score_desc(self):
        features = {"FEAT_007": "poc", "FEAT_049": "scoping"}
        results = recommend(features, top_n=5)
        scores = [score for _, score, _ in results]
        assert scores == sorted(scores, reverse=True)

    def test_each_result_has_action_score_reason(self):
        features = {"FEAT_007": "awareness"}
        results = recommend(features, top_n=3)
        for action, score, reason in results:
            assert isinstance(action, dict)
            assert "id" in action
            assert "name" in action
            assert isinstance(score, float)
            assert isinstance(reason, str)
            assert len(reason) > 0

    def test_min_score_filters_low_scores(self):
        features = {"FEAT_007": "awareness"}
        results = recommend(features, top_n=50, min_score=0.5)
        for _, score, _ in results:
            assert score >= 0.5

    def test_empty_features_returns_empty(self):
        results = recommend({}, top_n=5, min_score=0.5)
        assert results == []

    def test_korean_insurance_poc_scenario(self):
        features = {
            "FEAT_001": "enterprise",
            "FEAT_002": "finance",
            "FEAT_003": "korea",
            "FEAT_005": True,
            "FEAT_007": "poc",
            "FEAT_011": True,
            "FEAT_015": "engaged",
            "FEAT_049": "scoping",
            "FEAT_055": [],
            "FEAT_056": ["k_isms"],
            "FEAT_078": ["k_isms"],
        }
        results = recommend(features, top_n=5)
        action_ids = [a["id"] for a, _, _ in results]
        assert "ACT_037" in action_ids or "ACT_095" in action_ids
