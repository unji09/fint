from app.dashboard.chart_formatter import format_chart_data
from app.schemas.dashboard import WidgetType


class TestBarChart:
    def test_basic_bar(self):
        rows = [
            {"current_pipeline": "리드", "total": 3},
            {"current_pipeline": "제안", "total": 5},
            {"current_pipeline": "계약", "total": 2},
        ]
        result = format_chart_data(WidgetType.BAR, rows, x_column="current_pipeline", y_column="total")

        assert result["labels"] == ["리드", "제안", "계약"]
        assert result["values"] == [3, 5, 2]

    def test_empty_rows(self):
        result = format_chart_data(WidgetType.BAR, [], x_column="name", y_column="amount")

        assert result["labels"] == []
        assert result["values"] == []


class TestLineChart:
    def test_basic_line(self):
        rows = [
            {"week": "W1", "revenue": 100},
            {"week": "W2", "revenue": 200},
            {"week": "W3", "revenue": 150},
        ]
        result = format_chart_data(WidgetType.LINE, rows, x_column="week", y_column="revenue")

        assert result["labels"] == ["W1", "W2", "W3"]
        assert result["values"] == [100, 200, 150]


class TestPieChart:
    def test_basic_pie(self):
        rows = [
            {"industry": "IT", "count": 10},
            {"industry": "제조", "count": 7},
            {"industry": "금융", "count": 3},
        ]
        result = format_chart_data(WidgetType.PIE, rows, x_column="industry", y_column="count")

        assert result["labels"] == ["IT", "제조", "금융"]
        assert result["values"] == [10, 7, 3]


class TestKPI:
    def test_single_value(self):
        rows = [{"total_amount": 50000000}]
        result = format_chart_data(WidgetType.KPI, rows, y_column="total_amount")

        assert result["value"] == 50000000

    def test_empty_rows_returns_zero(self):
        result = format_chart_data(WidgetType.KPI, rows=[], y_column="total_amount")

        assert result["value"] == 0


class TestTable:
    def test_basic_table(self):
        rows = [
            {"name": "삼성전자", "amount": 1000},
            {"name": "LG전자", "amount": 500},
        ]
        result = format_chart_data(WidgetType.TABLE, rows)

        assert result["columns"] == ["name", "amount"]
        assert len(result["rows"]) == 2
        assert result["rows"][0] == {"name": "삼성전자", "amount": 1000}

    def test_empty_table(self):
        result = format_chart_data(WidgetType.TABLE, rows=[])

        assert result["columns"] == []
        assert result["rows"] == []
