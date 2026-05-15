"""Chart.js 호환 위젯 결과 포매터."""

from __future__ import annotations

from app.schemas.dashboard import InsightResult, WidgetType

_MAX_CHART_CATEGORIES = 20
_MAX_PIE_SEGMENTS = 10
_TABLE_MIN_COLUMNS = 5
_MAX_TITLE_LENGTH = 100

def format_result(
    insight: InsightResult,
    rows: list[dict],
    *,
    source_query: str | None,
    search_type: str,
) -> dict:
    """InsightResult + rows → Spring 전달용 result dict."""
    widget_type = _correct_widget_type(insight, rows)
    chart_type = _resolve_chart_type(widget_type, insight.chart_type, rows)
    title = (insight.title or "")[:_MAX_TITLE_LENGTH]

    return {
        "widget_type": widget_type.value,
        "chart_type": chart_type,
        "title": title,
        "config": _build_config(widget_type, chart_type, insight, rows),
        "data": _build_data(rows),
        "column_metadata": _build_column_metadata(insight),
        "insight_text": insight.insight_text,
        "key_findings": insight.key_findings,
        "data_summary": insight.data_summary,
        "suggested_chart_types": insight.suggested_chart_types,
        "source_query": source_query,
        "search_type": search_type,
    }


# --- widget type correction ---


def _correct_widget_type(insight: InsightResult, rows: list[dict]) -> WidgetType:
    wt = insight.widget_type
    if not rows:
        return wt
    if wt == WidgetType.CARD and len(rows) > 1:
        return WidgetType.TABLE
    if wt in (WidgetType.CARD, WidgetType.TABLE):
        return wt
    if len(rows) == 1 and not insight.labels_field and insight.datasets:
        return WidgetType.CARD
    if len(rows[0]) >= _TABLE_MIN_COLUMNS:
        return WidgetType.TABLE
    if len(rows) > _MAX_CHART_CATEGORIES:
        return WidgetType.TABLE
    return wt


def _resolve_chart_type(
    widget_type: WidgetType, insight_chart_type: str | None, rows: list[dict],
) -> str | None:
    if widget_type in (WidgetType.CARD, WidgetType.TABLE):
        return None
    if insight_chart_type == "pie" and rows and len(rows) > _MAX_PIE_SEGMENTS:
        return "bar"
    return insight_chart_type


# --- config builders ---


def _build_config(
    widget_type: WidgetType, chart_type: str | None, insight: InsightResult, rows: list[dict],
) -> dict:
    if widget_type == WidgetType.CARD:
        return _build_kpi_config(insight)
    if widget_type == WidgetType.TABLE:
        return _build_table_config(insight, rows)
    return _build_chart_config(chart_type, insight)


def _build_chart_config(chart_type: str | None, insight: InsightResult) -> dict:
    options: dict = {}
    if insight.x_label:
        options["xAxis"] = {"label": insight.x_label}
    if insight.y_label:
        y_axis: dict = {"label": insight.y_label}
        if insight.y_unit:
            y_axis["unit"] = insight.y_unit
        options["yAxis"] = y_axis
    options["legend"] = len(insight.datasets) > 1

    return {
        "chart": {"type": chart_type, "variant": insight.chart_variant},
        "data": {
            "labelsField": insight.labels_field,
            "datasets": [
                {"label": d.label, "valueField": d.value_field}
                for d in insight.datasets
            ],
        },
        "options": options,
        "display": {
            "format": insight.display_format,
            "unit": insight.y_unit,
            "emptyMessage": "표시할 데이터가 없습니다",
        },
    }


def _build_kpi_config(insight: InsightResult) -> dict:
    value_field = insight.datasets[0].value_field if insight.datasets else None
    return {
        "data": {"valueField": value_field},
        "display": {
            "label": insight.title,
            "format": insight.display_format,
            "unit": insight.y_unit,
        },
    }


def _build_table_config(insight: InsightResult, rows: list[dict]) -> dict:
    columns: list[dict] = []
    if insight.datasets:
        if insight.labels_field:
            columns.append({
                "label": insight.x_label or insight.labels_field,
                "field": insight.labels_field,
            })
        for ds in insight.datasets:
            col: dict = {"label": ds.label, "field": ds.value_field}
            if insight.display_format:
                col["format"] = insight.display_format
            if insight.y_unit:
                col["unit"] = insight.y_unit
            columns.append(col)
    elif rows:
        columns = [{"label": k, "field": k} for k in rows[0].keys()]

    return {
        "columns": columns,
        "display": {"emptyMessage": "표시할 데이터가 없습니다"},
    }


# --- data ---


def _build_data(rows: list[dict]) -> dict:
    return {
        "columns": list(rows[0].keys()) if rows else [],
        "rows": rows,
        "totalRowCount": len(rows),
    }


# --- column metadata ---


def _build_column_metadata(insight: InsightResult) -> dict:
    metadata: dict = {}
    if insight.labels_field:
        metadata[insight.labels_field] = {
            "label": insight.x_label or insight.labels_field,
            "type": "text",
        }
    for ds in insight.datasets:
        entry: dict = {"label": ds.label, "type": "numeric"}
        if insight.y_unit:
            entry["unit"] = insight.y_unit
        if insight.display_format:
            entry["format"] = insight.display_format
        metadata[ds.value_field] = entry
    return metadata
