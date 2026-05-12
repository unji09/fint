from app.dashboard.chart_formatter import format_chart_data
from app.schemas.dashboard import WidgetType


class TestBarChart:
    def test_basic_bar(self):
        rows = [
            {"current_pipeline": "리드", "total": 3},
            {"current_pipeline": "제안", "total": 5},
            {"current_pipeline": "계약", "total": 2},
        ]
        wt, result = format_chart_data(WidgetType.BAR, rows, x_column="current_pipeline", y_column="total")

        assert wt == WidgetType.BAR
        assert result["labels"] == ["리드", "제안", "계약"]
        assert result["values"] == [3, 5, 2]

    def test_empty_rows(self):
        wt, result = format_chart_data(WidgetType.BAR, [], x_column="name", y_column="amount")

        assert wt == WidgetType.BAR
        assert result["labels"] == []
        assert result["values"] == []


class TestLineChart:
    def test_basic_line(self):
        rows = [
            {"week": "W1", "revenue": 100},
            {"week": "W2", "revenue": 200},
            {"week": "W3", "revenue": 150},
        ]
        wt, result = format_chart_data(WidgetType.LINE, rows, x_column="week", y_column="revenue")

        assert wt == WidgetType.LINE
        assert result["labels"] == ["W1", "W2", "W3"]
        assert result["values"] == [100, 200, 150]


class TestPieChart:
    def test_basic_pie(self):
        rows = [
            {"industry": "IT", "count": 10},
            {"industry": "제조", "count": 7},
            {"industry": "금융", "count": 3},
        ]
        wt, result = format_chart_data(WidgetType.PIE, rows, x_column="industry", y_column="count")

        assert wt == WidgetType.PIE
        assert result["labels"] == ["IT", "제조", "금융"]
        assert result["values"] == [10, 7, 3]


class TestKPI:
    def test_single_value(self):
        rows = [{"total_amount": 50000000}]
        wt, result = format_chart_data(WidgetType.KPI, rows, y_column="total_amount")

        assert wt == WidgetType.KPI
        assert result["value"] == 50000000

    def test_empty_rows_returns_zero(self):
        wt, result = format_chart_data(WidgetType.KPI, rows=[], y_column="total_amount")

        assert wt == WidgetType.KPI
        assert result["value"] == 0


class TestTable:
    def test_basic_table(self):
        rows = [
            {"name": "삼성전자", "amount": 1000},
            {"name": "LG전자", "amount": 500},
        ]
        wt, result = format_chart_data(WidgetType.TABLE, rows)

        assert wt == WidgetType.TABLE
        assert result["columns"] == ["name", "amount"]
        assert len(result["rows"]) == 2
        assert result["rows"][0] == {"name": "삼성전자", "amount": 1000}

    def test_empty_table(self):
        wt, result = format_chart_data(WidgetType.TABLE, rows=[])

        assert wt == WidgetType.TABLE
        assert result["columns"] == []
        assert result["rows"] == []


class TestWidgetTypeCorrection:
    """LLM이 부적절한 위젯 타입을 선택했을 때 데이터 형태 기반 보정 검증."""

    def test_single_aggregate_row_corrected_to_kpi(self):
        """행 1개 + 수치 컬럼 1개면 BAR 대신 KPI로 보정."""
        rows = [{"COUNT(*)": 42}]
        wt, result = format_chart_data(WidgetType.BAR, rows, x_column=None, y_column="COUNT(*)")
        assert wt == WidgetType.KPI
        assert result["value"] == 42

    def test_many_columns_corrected_to_table(self):
        """컬럼 5개 이상이면 BAR 대신 TABLE로 보정."""
        rows = [
            {"name": "A", "industry": "IT", "phone": "010", "email": "a@b.c", "city": "서울"},
        ]
        wt, result = format_chart_data(WidgetType.BAR, rows, x_column="name", y_column="industry")
        assert wt == WidgetType.TABLE
        assert "columns" in result
        assert "rows" in result

    def test_four_columns_not_corrected(self):
        """컬럼 4개는 TABLE로 보정하지 않음 (임계값은 5)."""
        rows = [
            {"name": "A", "industry": "IT", "phone": "010", "email": "a@b.c"},
            {"name": "B", "industry": "제조", "phone": "011", "email": "b@c.d"},
        ]
        wt, result = format_chart_data(WidgetType.BAR, rows, x_column="name", y_column="industry")
        assert wt == WidgetType.BAR
        assert "labels" in result

    def test_too_many_categories_corrected_to_table(self):
        """카테고리 20개 초과 시 차트 대신 TABLE로 보정."""
        rows = [{"name": f"Company {i}", "value": i} for i in range(21)]
        wt, result = format_chart_data(WidgetType.BAR, rows, x_column="name", y_column="value")
        assert wt == WidgetType.TABLE
        assert "columns" in result
        assert "rows" in result

    def test_exactly_20_categories_not_corrected(self):
        """카테고리 정확히 20개는 TABLE로 보정하지 않음."""
        rows = [{"name": f"Company {i}", "value": i} for i in range(20)]
        wt, result = format_chart_data(WidgetType.BAR, rows, x_column="name", y_column="value")
        assert wt == WidgetType.BAR
        assert "labels" in result
        assert len(result["labels"]) == 20

    def test_normal_bar_not_corrected(self):
        """정상 범위의 BAR 데이터는 보정하지 않음."""
        rows = [
            {"stage": "리드", "count": 10},
            {"stage": "제안", "count": 5},
        ]
        wt, result = format_chart_data(WidgetType.BAR, rows, x_column="stage", y_column="count")
        assert wt == WidgetType.BAR
        assert "labels" in result
        assert "values" in result

    def test_table_not_corrected(self):
        """TABLE 타입은 보정 대상이 아님."""
        rows = [{"name": "A", "value": 1}]
        wt, result = format_chart_data(WidgetType.TABLE, rows)
        assert wt == WidgetType.TABLE
        assert "columns" in result

    def test_pie_too_many_segments_corrected_to_bar(self):
        """PIE 세그먼트 10개 초과 시 BAR로 보정."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(15)]
        wt, result = format_chart_data(WidgetType.PIE, rows, x_column="cat", y_column="val")
        assert wt == WidgetType.BAR
        assert "labels" in result
        assert "values" in result
        assert len(result["labels"]) == 15

    def test_exactly_10_pie_segments_not_corrected(self):
        """PIE 세그먼트 정확히 10개는 보정하지 않음."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(10)]
        wt, result = format_chart_data(WidgetType.PIE, rows, x_column="cat", y_column="val")
        assert wt == WidgetType.PIE
        assert result["labels"] == [f"Cat{i}" for i in range(10)]

    def test_exactly_11_pie_segments_corrected_to_bar(self):
        """PIE 세그먼트 11개는 BAR로 보정."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(11)]
        wt, result = format_chart_data(WidgetType.PIE, rows, x_column="cat", y_column="val")
        assert wt == WidgetType.BAR
        assert len(result["labels"]) == 11

    def test_pie_within_limit_not_corrected(self):
        """PIE 세그먼트 10개 이하는 보정 안 함."""
        rows = [{"cat": f"Cat{i}", "val": i} for i in range(5)]
        wt, result = format_chart_data(WidgetType.PIE, rows, x_column="cat", y_column="val")
        assert wt == WidgetType.PIE
        assert result["labels"] == ["Cat0", "Cat1", "Cat2", "Cat3", "Cat4"]

    def test_kpi_not_corrected(self):
        """KPI 타입은 보정 대상이 아님."""
        rows = [{"total": 100}]
        wt, result = format_chart_data(WidgetType.KPI, rows, y_column="total")
        assert wt == WidgetType.KPI
        assert result["value"] == 100

    def test_line_too_many_points_corrected_to_table(self):
        """LINE 차트도 카테고리 20개 초과 시 TABLE로 보정."""
        rows = [{"month": f"M{i}", "revenue": i * 100} for i in range(25)]
        wt, result = format_chart_data(WidgetType.LINE, rows, x_column="month", y_column="revenue")
        assert wt == WidgetType.TABLE
        assert "columns" in result
        assert len(result["rows"]) == 25
