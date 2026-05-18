"""Unit tests for strategy gap_check module."""
import pytest

from app.strategy.gap_check import check_gaps


class TestCheckGaps:
    def test_no_stage_fails_hard(self):
        result = check_gaps({})
        assert result["ok_to_recommend"] is False
        assert "FEAT_007" in result["missing_hard"]

    def test_stage_present_passes_hard(self):
        result = check_gaps({"FEAT_007": "discovery"})
        assert result["ok_to_recommend"] is True
        assert result["missing_hard"] == []

    def test_soft_missing_detected(self):
        result = check_gaps({"FEAT_007": "discovery"})
        assert "FEAT_002" in result["missing_soft"]
        assert "FEAT_003" in result["missing_soft"]
        assert "FEAT_001" in result["missing_soft"]

    def test_soft_present_not_missing(self):
        result = check_gaps({
            "FEAT_007": "discovery",
            "FEAT_002": "finance",
            "FEAT_003": "korea",
            "FEAT_001": "enterprise",
        })
        assert result["missing_soft"] == []

    def test_poc_conditional_requires_poc_status(self):
        result = check_gaps({"FEAT_007": "poc"})
        assert "FEAT_049" in result["missing_conditional"]

    def test_negotiation_conditional_requires_objections(self):
        result = check_gaps({"FEAT_007": "negotiation"})
        assert "FEAT_059" in result["missing_conditional"]

    def test_conditional_satisfied(self):
        result = check_gaps({"FEAT_007": "poc", "FEAT_049": "scoping"})
        assert result["missing_conditional"] == []

    def test_questions_generated_for_missing(self):
        result = check_gaps({})
        assert "FEAT_007" in result["questions"]
        assert len(result["questions"]["FEAT_007"]) > 0

    def test_no_questions_when_all_present(self):
        result = check_gaps({
            "FEAT_007": "awareness",
            "FEAT_002": "finance",
            "FEAT_003": "korea",
            "FEAT_001": "enterprise",
        })
        assert result["questions"] == {}
