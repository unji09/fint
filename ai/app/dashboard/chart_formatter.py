"""위젯 타입별 데이터 정규화."""

from __future__ import annotations

from app.schemas.dashboard import WidgetType


def format_chart_data(
    widget_type: WidgetType,
    rows: list[dict],
    *,
    x_column: str | None = None,
    y_column: str | None = None,
) -> dict:
    if widget_type == WidgetType.KPI:
        return _format_kpi(rows, y_column=y_column)
    if widget_type == WidgetType.TABLE:
        return _format_table(rows)
    return _format_xy(rows, x_column=x_column, y_column=y_column)


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
