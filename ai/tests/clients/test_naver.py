"""NaverNewsClient 단위 테스트."""
from datetime import datetime, timezone

import pytest

from app.clients.naver import NaverNewsItem, _parse_pub_date, _strip_html


class TestStripHtml:
    def test_removes_bold_tags(self):
        assert _strip_html("<b>삼성전자</b> 반도체") == "삼성전자 반도체"

    def test_unescapes_html_entities(self):
        assert _strip_html("A &amp; B &lt;C&gt;") == "A & B <C>"

    def test_empty_string(self):
        assert _strip_html("") == ""

    def test_plain_text_unchanged(self):
        assert _strip_html("뉴스 제목") == "뉴스 제목"

    def test_nested_tags(self):
        assert _strip_html("<p><b>제목</b></p>") == "제목"


class TestParsePubDate:
    def test_rfc1123_format(self):
        dt = _parse_pub_date("Mon, 12 May 2025 09:30:00 +0900")
        assert dt.year == 2025
        assert dt.month == 5
        assert dt.day == 12

    def test_returns_timezone_aware(self):
        dt = _parse_pub_date("Mon, 12 May 2025 09:30:00 +0900")
        assert dt.tzinfo is not None


class TestNaverNewsItem:
    def test_frozen_dataclass(self):
        item = NaverNewsItem(
            title="제목",
            original_link="https://example.com/original",
            link="https://n.news.naver.com/123",
            description="요약",
            pub_date=datetime(2025, 5, 12, tzinfo=timezone.utc),
        )
        assert item.title == "제목"
        with pytest.raises(AttributeError):
            item.title = "변경"