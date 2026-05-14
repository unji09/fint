"""Function Calling tool 정의 스키마 검증."""

from app.dashboard.tools import (
    ALL_TOOLS,
    TOOL_QUERY_STRUCTURED,
    TOOL_REJECT,
    TOOL_SEARCH_NEWS,
    parse_tool_calls,
)


class TestToolSchemas:
    def test_all_tools_has_three_entries(self):
        assert len(ALL_TOOLS) == 3

    def test_each_tool_has_function_type(self):
        for tool in ALL_TOOLS:
            assert tool["type"] == "function"
            assert "function" in tool
            assert "name" in tool["function"]
            assert "parameters" in tool["function"]

    def test_query_structured_has_required_table_field(self):
        params = TOOL_QUERY_STRUCTURED["function"]["parameters"]
        assert "table" in params["properties"]
        assert "table" in params["required"]

    def test_search_news_has_search_text_field(self):
        params = TOOL_SEARCH_NEWS["function"]["parameters"]
        assert "search_text" in params["properties"]

    def test_reject_has_reason_field(self):
        params = TOOL_REJECT["function"]["parameters"]
        assert "reason" in params["properties"]
        assert "reason" in params["required"]


class TestParseToolCalls:
    def test_empty_when_no_tool_calls(self):
        class FakeMessage:
            tool_calls = None

        assert parse_tool_calls(FakeMessage()) == []

    def test_empty_when_no_attr(self):
        assert parse_tool_calls(object()) == []

    def test_parses_single_tool_call(self):
        class FakeFunction:
            name = "query_structured_data"
            arguments = '{"table": "deals", "columns": ["title"]}'

        class FakeToolCall:
            id = "call_123"
            function = FakeFunction()

        class FakeMessage:
            tool_calls = [FakeToolCall()]

        result = parse_tool_calls(FakeMessage())
        assert len(result) == 1
        assert result[0]["name"] == "query_structured_data"
        assert result[0]["arguments"]["table"] == "deals"

    def test_parses_multiple_tool_calls(self):
        class FakeFunction1:
            name = "query_structured_data"
            arguments = '{"table": "deals", "columns": ["title"]}'

        class FakeFunction2:
            name = "search_news"
            arguments = '{"search_text": "삼성"}'

        class FakeToolCall:
            def __init__(self, tc_id, func):
                self.id = tc_id
                self.function = func

        class FakeMessage:
            tool_calls = [
                FakeToolCall("call_1", FakeFunction1()),
                FakeToolCall("call_2", FakeFunction2()),
            ]

        result = parse_tool_calls(FakeMessage())
        assert len(result) == 2
        assert result[0]["name"] == "query_structured_data"
        assert result[1]["name"] == "search_news"
