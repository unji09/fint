from app.dashboard.schema_context import (
    ALLOWED_TABLES,
    build_llm_schema_prompt,
    get_allowed_columns,
    get_allowed_join_targets,
    get_filterable_columns,
)


class TestTemperatureHistorySchema:
    def test_mood_score_column_exists(self):
        cols = get_allowed_columns("temperature_history")
        assert "mood_score" in cols

    def test_activity_id_column_exists(self):
        cols = get_allowed_columns("temperature_history")
        assert "activity_id" in cols

    def test_key_signals_column_exists(self):
        cols = get_allowed_columns("temperature_history")
        assert "key_signals" in cols

    def test_mood_score_is_filterable(self):
        filterable = get_filterable_columns("temperature_history")
        assert "mood_score" in filterable

    def test_activity_id_not_filterable(self):
        filterable = get_filterable_columns("temperature_history")
        assert "activity_id" not in filterable

    def test_key_signals_not_filterable(self):
        filterable = get_filterable_columns("temperature_history")
        assert "key_signals" not in filterable

    def test_mood_description_includes_enum_values(self):
        meta = ALLOWED_TABLES["temperature_history"]
        mood_col = next(c for c in meta.columns if c.name == "mood")
        assert "RAINBOW" in mood_col.description
        assert "THUNDER" in mood_col.description

    def test_join_to_activities_exists(self):
        joins = get_allowed_join_targets("temperature_history")
        assert "activities" in joins


class TestSchemaPromptExamples:
    """build_llm_schema_prompt 결과에 컬럼별 예시값이 포함되는지 검증."""

    def test_prompt_includes_example_marker(self):
        prompt = build_llm_schema_prompt()
        assert "예:" in prompt

    def test_deals_amount_description_has_example(self):
        prompt = build_llm_schema_prompt()
        lines = [l for l in prompt.split("\n") if "amount" in l and "NUMERIC" in l]
        assert any("예:" in l for l in lines)

    def test_current_pipeline_has_stage_examples(self):
        prompt = build_llm_schema_prompt()
        lines = [l for l in prompt.split("\n") if "current_pipeline" in l]
        assert any("예:" in l for l in lines)


class TestActivitiesSchema:
    def test_mood_status_column_exists(self):
        cols = get_allowed_columns("activities")
        assert "mood_status" in cols

    def test_mood_status_is_filterable(self):
        filterable = get_filterable_columns("activities")
        assert "mood_status" in filterable
