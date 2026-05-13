from app.dashboard.schema_context import (
    ALLOWED_TABLES,
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


class TestActivitiesSchema:
    def test_mood_status_column_exists(self):
        cols = get_allowed_columns("activities")
        assert "mood_status" in cols

    def test_mood_status_is_filterable(self):
        filterable = get_filterable_columns("activities")
        assert "mood_status" in filterable
