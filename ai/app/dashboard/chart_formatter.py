"""위젯 타입별 데이터 정규화."""

from __future__ import annotations

from app.schemas.dashboard import WidgetType


_MAX_CHART_CATEGORIES = 20
_MAX_PIE_SEGMENTS = 10
_TABLE_MIN_COLUMNS = 5


def format_chart_data(
    widget_type: WidgetType,
    rows: list[dict],
    *,
    x_column: str | None = None,
    y_column: str | None = None,
) -> tuple[WidgetType, dict]:
    widget_type = _correct_widget_type(widget_type, rows, x_column=x_column, y_column=y_column)

    if widget_type == WidgetType.KPI:
        return widget_type, _format_kpi(rows, y_column=y_column)
    if widget_type == WidgetType.TABLE:
        return widget_type, _format_table(rows)
    return widget_type, _format_xy(rows, x_column=x_column, y_column=y_column)


def _correct_widget_type(
    widget_type: WidgetType,
    rows: list[dict],
    *,
    x_column: str | None,
    y_column: str | None,
) -> WidgetType:
    if widget_type in (WidgetType.KPI, WidgetType.TABLE):
        return widget_type
    if not rows:
        return widget_type
    if len(rows) == 1 and not x_column and y_column:
        return WidgetType.KPI
    col_count = len(rows[0])
    if col_count >= _TABLE_MIN_COLUMNS:
        return WidgetType.TABLE
    if len(rows) > _MAX_CHART_CATEGORIES:
        return WidgetType.TABLE
    if widget_type == WidgetType.PIE and len(rows) > _MAX_PIE_SEGMENTS:
        return WidgetType.BAR
    return widget_type


def _format_xy(rows: list[dict], *, x_column: str | None, y_column: str | None) -> dict:
    if not rows or not x_column or not y_column:
        return {"labels": [], "values": []}
    return {
        "labels": [row.get(x_column) for row in rows],
        "values": [row.get(y_column, 0) for row in rows],
    }


def _format_kpi(rows: list[dict], *, y_column: str | None) -> dict:
    if not rows or not y_column:
        return {"value": 0}
    return {"value": rows[0].get(y_column, 0)}


def _format_table(rows: list[dict]) -> dict:
    if not rows:
        return {"columns": [], "rows": []}
    return {
        "columns": list(rows[0].keys()),
        "rows": rows,
    }
