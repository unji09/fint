from app.dashboard.chart_formatter import format_result
from app.schemas.dashboard import DatasetSpec, InsightResult, WidgetType


def _make_insight(
    widget_type=WidgetType.CHART,
    chart_type=None,
    chart_variant=None,
    title="테스트",
    labels_field=None,
    datasets=None,
    x_label=None,
    y_label=None,
    y_unit=None,
    display_format=None,
    suggested_chart_types=None,
) -> InsightResult:
    return InsightResult(
        widget_type=widget_type,
        chart_type=chart_type,
        chart_variant=chart_variant,
        title=title,
        insight_text="인사이트",
        key_findings=["발견1"],
        data_summary="요약",
        labels_field=labels_field,
        datasets=datasets or [],
        x_label=x_label,
        y_label=y_label,
        y_unit=y_unit,
        display_format=display_format,
        suggested_chart_types=suggested_chart_types or [],
    )


def _fmt(insight, rows, **kw):
    defaults = {"source_query": None, "search_type": "STRUCTURED"}
    defaults.update(kw)
    return format_result(insight, rows, **defaults)


class TestBarChart:
    def test_basic_bar(self):
        rows = [
            {"current_pipeline": "리드", "total": 3},
            {"current_pipeline": "제안", "total": 5},
            {"current_pipeline": "계약", "total": 2},
        ]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="bar",
            labels_field="current_pipeline",
            datasets=[DatasetSpec(label="건수", value_field="total")],
            x_label="파이프라인",
            y_label="건수",
        )
        result = _fmt(insight, rows)

        assert result["widget_type"] == "CHART"
        assert result["chart_type"] == "bar"
        assert result["config"]["data"]["labelsField"] == "current_pipeline"
        assert result["config"]["data"]["datasets"][0]["valueField"] == "total"
        assert result["data"]["rows"] == rows
        assert result["data"]["totalRowCount"] == 3

    def test_empty_rows(self):
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="name",
            datasets=[DatasetSpec(label="금액", value_field="amount")],
        )
        result = _fmt(insight, [])

        assert result["widget_type"] == "CHART"
        assert result["data"]["rows"] == []
        assert result["data"]["totalRowCount"] == 0
        assert result["data"]["columns"] == []


class TestLineChart:
    def test_basic_line(self):
        rows = [
            {"week": "W1", "revenue": 100},
            {"week": "W2", "revenue": 200},
            {"week": "W3", "revenue": 150},
        ]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="line",
            labels_field="week",
            datasets=[DatasetSpec(label="매출", value_field="revenue")],
        )
        result = _fmt(insight, rows)

        assert result["widget_type"] == "CHART"
        assert result["chart_type"] == "line"
        assert result["config"]["chart"]["type"] == "line"


class TestPieChart:
    def test_basic_pie(self):
        rows = [
            {"industry": "IT", "count": 10},
            {"industry": "제조", "count": 7},
            {"industry": "금융", "count": 3},
        ]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="pie",
            labels_field="industry",
            datasets=[DatasetSpec(label="고객사 수", value_field="count")],
        )
        result = _fmt(insight, rows)

        assert result["widget_type"] == "CHART"
        assert result["chart_type"] == "pie"


class TestKPI:
    def test_single_value(self):
        rows = [{"total_amount": 50000000}]
        insight = _make_insight(
            widget_type=WidgetType.CARD,
            datasets=[DatasetSpec(label="총 매출", value_field="total_amount")],
            display_format="currency",
            y_unit="원",
        )
        result = _fmt(insight, rows)

        assert result["widget_type"] == "CARD"
        assert result["chart_type"] is None
        assert result["config"]["data"]["valueField"] == "total_amount"
        assert result["config"]["display"]["format"] == "currency"
        assert result["data"]["rows"] == rows

    def test_empty_rows_returns_empty(self):
        insight = _make_insight(
            widget_type=WidgetType.CARD,
            datasets=[DatasetSpec(label="총 매출", value_field="total_amount")],
        )
        result = _fmt(insight, [])

        assert result["widget_type"] == "CARD"
        assert result["data"]["rows"] == []
        assert result["data"]["totalRowCount"] == 0


class TestTable:
    def test_basic_table(self):
        rows = [
            {"name": "삼성전자", "amount": 1000},
            {"name": "LG전자", "amount": 500},
        ]
        insight = _make_insight(
            widget_type=WidgetType.TABLE,
            labels_field="name",
            datasets=[DatasetSpec(label="금액", value_field="amount")],
            x_label="고객사명",
        )
        result = _fmt(insight, rows)

        assert result["widget_type"] == "TABLE"
        assert result["chart_type"] is None
        assert result["data"]["columns"] == ["name", "amount"]
        assert result["data"]["totalRowCount"] == 2

    def test_empty_table(self):
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, [])

        assert result["widget_type"] == "TABLE"
        assert result["data"]["columns"] == []
        assert result["data"]["rows"] == []

    def test_table_config_from_rows_fallback(self):
        """datasets가 없으면 rows 키로 columns 생성."""
        rows = [{"name": "A", "value": 1}]
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, rows)

        fields = [c["field"] for c in result["config"]["columns"]]
        assert "name" in fields
        assert "value" in fields


class TestWidgetTypeCorrection:
    """LLM이 부적절한 위젯 타입을 선택했을 때 데이터 형태 기반 보정 검증."""

    def test_single_aggregate_row_corrected_to_kpi(self):
        """행 1개 + labels_field 없음 + datasets 있음 → KPI로 보정."""
        rows = [{"COUNT(*)": 42}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            datasets=[DatasetSpec(label="건수", value_field="COUNT(*)")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CARD"

    def test_many_columns_corrected_to_table(self):
        """컬럼 5개 이상이면 BAR 대신 TABLE로 보정."""
        rows = [
            {"name": "A", "industry": "IT", "phone": "010", "email": "a@b.c", "city": "서울"},
        ]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="name",
            datasets=[DatasetSpec(label="업종", value_field="industry")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "TABLE"

    def test_four_columns_not_corrected(self):
        """컬럼 4개는 TABLE로 보정하지 않음 (임계값은 5)."""
        rows = [
            {"name": "A", "industry": "IT", "phone": "010", "email": "a@b.c"},
            {"name": "B", "industry": "제조", "phone": "011", "email": "b@c.d"},
        ]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="name",
            datasets=[DatasetSpec(label="업종", value_field="industry")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CHART"

    def test_too_many_categories_corrected_to_table(self):
        """카테고리 20개 초과 시 차트 대신 TABLE로 보정."""
        rows = [{"name": f"Company {i}", "value": i} for i in range(21)]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="name",
            datasets=[DatasetSpec(label="값", value_field="value")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "TABLE"

    def test_exactly_20_categories_not_corrected(self):
        """카테고리 정확히 20개는 TABLE로 보정하지 않음."""
        rows = [{"name": f"Company {i}", "value": i} for i in range(20)]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="name",
            datasets=[DatasetSpec(label="값", value_field="value")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CHART"

    def test_normal_bar_not_corrected(self):
        """정상 범위의 BAR 데이터는 보정하지 않음."""
        rows = [
            {"stage": "리드", "count": 10},
            {"stage": "제안", "count": 5},
        ]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="stage",
            datasets=[DatasetSpec(label="건수", value_field="count")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CHART"

    def test_table_not_corrected(self):
        """TABLE 타입은 보정 대상이 아님."""
        rows = [{"name": "A", "value": 1}]
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, rows)
        assert result["widget_type"] == "TABLE"

    def test_pie_too_many_segments_corrected_to_bar(self):
        """PIE 세그먼트 10개 초과 시 chart_type을 bar로 보정."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(15)]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="pie",
            labels_field="cat",
            datasets=[DatasetSpec(label="값", value_field="val")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CHART"
        assert result["chart_type"] == "bar"

    def test_exactly_10_pie_segments_not_corrected(self):
        """PIE 세그먼트 정확히 10개는 보정하지 않음."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(10)]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="pie",
            labels_field="cat",
            datasets=[DatasetSpec(label="값", value_field="val")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CHART"
        assert result["chart_type"] == "pie"

    def test_exactly_11_pie_segments_corrected_to_bar(self):
        """PIE 세그먼트 11개는 chart_type을 bar로 보정."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(11)]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="pie",
            labels_field="cat",
            datasets=[DatasetSpec(label="값", value_field="val")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CHART"
        assert result["chart_type"] == "bar"

    def test_pie_within_limit_not_corrected(self):
        """PIE 세그먼트 10개 이하는 보정 안 함."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(5)]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="pie",
            labels_field="cat",
            datasets=[DatasetSpec(label="값", value_field="val")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CHART"
        assert result["chart_type"] == "pie"

    def test_kpi_single_row_not_corrected(self):
        """KPI 타입 + 1행은 KPI 유지."""
        rows = [{"total": 100}]
        insight = _make_insight(
            widget_type=WidgetType.CARD,
            datasets=[DatasetSpec(label="합계", value_field="total")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "CARD"

    def test_kpi_multi_row_corrected_to_table(self):
        """KPI인데 상세 행이 여러 개면 TABLE로 보정."""
        rows = [
            {"title": "딜A", "amount": 80000000},
            {"title": "딜B", "amount": 70000000},
        ]
        insight = _make_insight(
            widget_type=WidgetType.CARD,
            datasets=[DatasetSpec(label="금액", value_field="amount")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "TABLE"

    def test_line_too_many_points_corrected_to_table(self):
        """LINE 차트도 카테고리 20개 초과 시 TABLE로 보정."""
        rows = [{"month": f"M{i}", "revenue": i * 100} for i in range(25)]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="month",
            datasets=[DatasetSpec(label="매출", value_field="revenue")],
        )
        result = _fmt(insight, rows)
        assert result["widget_type"] == "TABLE"


class TestChartConfig:
    """Chart.js config 구조 검증."""

    def test_chart_config_has_chart_section(self):
        rows = [{"x": "a", "y": 1}, {"x": "b", "y": 2}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="bar",
            labels_field="x",
            datasets=[DatasetSpec(label="값", value_field="y")],
        )
        result = _fmt(insight, rows)
        assert result["config"]["chart"]["type"] == "bar"
        assert result["config"]["chart"]["variant"] is None

    def test_chart_config_legend_false_single_dataset(self):
        rows = [{"x": "a", "y": 1}, {"x": "b", "y": 2}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="x",
            datasets=[DatasetSpec(label="값", value_field="y")],
        )
        result = _fmt(insight, rows)
        assert result["config"]["options"]["legend"] is False

    def test_chart_config_legend_true_multiple_datasets(self):
        rows = [{"x": "a", "y1": 1, "y2": 2}, {"x": "b", "y1": 3, "y2": 4}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="x",
            datasets=[
                DatasetSpec(label="시리즈1", value_field="y1"),
                DatasetSpec(label="시리즈2", value_field="y2"),
            ],
        )
        result = _fmt(insight, rows)
        assert result["config"]["options"]["legend"] is True

    def test_chart_config_axis_labels(self):
        rows = [{"x": "a", "y": 1}, {"x": "b", "y": 2}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="x",
            datasets=[DatasetSpec(label="값", value_field="y")],
            x_label="카테고리",
            y_label="수량",
            y_unit="건",
        )
        result = _fmt(insight, rows)
        assert result["config"]["options"]["xAxis"]["label"] == "카테고리"
        assert result["config"]["options"]["yAxis"]["label"] == "수량"
        assert result["config"]["options"]["yAxis"]["unit"] == "건"

    def test_chart_type_passthrough(self):
        """chart_type이 지정되면 그대로 전달."""
        rows = [{"x": "a", "y": 1}, {"x": "b", "y": 2}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="line",
            labels_field="x",
            datasets=[DatasetSpec(label="값", value_field="y")],
        )
        result = _fmt(insight, rows)
        assert result["chart_type"] == "line"


class TestDataFormat:
    """data 구조 검증."""

    def test_data_has_columns_rows_total(self):
        rows = [{"a": 1, "b": 2}]
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, rows)

        assert result["data"]["columns"] == ["a", "b"]
        assert result["data"]["rows"] == rows
        assert result["data"]["totalRowCount"] == 1

    def test_totalRowCount_equals_len_rows(self):
        rows = [{"v": i} for i in range(7)]
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, rows)
        assert result["data"]["totalRowCount"] == 7


class TestColumnMetadata:
    """column_metadata 생성 검증."""

    def test_metadata_from_insight_fields(self):
        rows = [{"name": "A", "COUNT(*)": 5}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            labels_field="name",
            datasets=[DatasetSpec(label="딜 수", value_field="COUNT(*)")],
            x_label="고객사명",
            y_unit="건",
            display_format="number",
        )
        result = _fmt(insight, rows)

        meta = result["column_metadata"]
        assert meta["name"]["label"] == "고객사명"
        assert meta["COUNT(*)"]["label"] == "딜 수"
        assert meta["COUNT(*)"]["unit"] == "건"

    def test_empty_metadata_when_no_insight_fields(self):
        rows = [{"a": 1}]
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, rows)
        assert result["column_metadata"] == {}


class TestTitleTruncation:
    def test_long_title_truncated(self):
        rows = [{"a": 1}]
        insight = _make_insight(widget_type=WidgetType.TABLE, title="A" * 200)
        result = _fmt(insight, rows)
        assert len(result["title"]) == 100

    def test_short_title_not_truncated(self):
        rows = [{"a": 1}]
        insight = _make_insight(widget_type=WidgetType.TABLE, title="짧은 제목")
        result = _fmt(insight, rows)
        assert result["title"] == "짧은 제목"


class TestResultFields:
    """result 최상위 필드 존재 검증."""

    def test_all_required_fields_present(self):
        rows = [{"x": "a", "y": 1}, {"x": "b", "y": 2}]
        insight = _make_insight(
            widget_type=WidgetType.CHART,
            chart_type="bar",
            labels_field="x",
            datasets=[DatasetSpec(label="값", value_field="y")],
            suggested_chart_types=["bar", "line"],
        )
        result = _fmt(insight, rows, source_query="SELECT ...", search_type="STRUCTURED")

        required_keys = [
            "widget_type", "chart_type", "title", "config", "data",
            "column_metadata", "insight_text", "key_findings",
            "data_summary", "suggested_chart_types", "source_query", "search_type",
        ]
        for key in required_keys:
            assert key in result, f"Missing key: {key}"

    def test_source_query_and_search_type_passed_through(self):
        rows = [{"a": 1}]
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, rows, source_query="SELECT 1", search_type="SEMANTIC")

        assert result["source_query"] == "SELECT 1"
        assert result["search_type"] == "SEMANTIC"


class TestHumanizeColumn:
    """TABLE 컬럼명 한글화 검증."""

    def test_known_column_translated(self):
        from app.dashboard.chart_formatter import _humanize_column
        assert _humanize_column("account_id") == "고객사ID"
        assert _humanize_column("created_at") == "생성일"
        assert _humanize_column("amount") == "금액"

    def test_aggregate_translated(self):
        from app.dashboard.chart_formatter import _humanize_column
        assert _humanize_column("SUM(amount)") == "금액 합계"
        assert _humanize_column("COUNT(*)") == "건수"
        assert _humanize_column("AVG(probability)") == "성공 확률 평균"

    def test_date_trunc_translated(self):
        from app.dashboard.chart_formatter import _humanize_column
        assert _humanize_column("DATE_TRUNC('month', created_at)") == "월"
        assert _humanize_column("DATE_TRUNC('year', created_at)") == "연도"

    def test_unknown_column_returns_original(self):
        from app.dashboard.chart_formatter import _humanize_column
        assert _humanize_column("some_custom_field") == "some_custom_field"

    def test_table_fallback_uses_humanized_labels(self):
        rows = [{"account_id": 1, "name": "삼성", "created_at": "2026-01-01"}]
        insight = _make_insight(widget_type=WidgetType.TABLE)
        result = _fmt(insight, rows)

        labels = {c["label"] for c in result["config"]["columns"]}
        assert "고객사ID" in labels
        assert "이름" in labels
        assert "생성일" in labels
