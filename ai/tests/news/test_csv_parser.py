from __future__ import annotations

import tempfile
from pathlib import Path

from app.news.csv_parser import NewsRecord, parse_csv

HEADER = "company|title|link|published|category|category_str|reporter|article\n"

SINGLE_LINE_ARTICLE = (
    "한경|테스트 뉴스 제목|https://example.com/1|2025-01-15 09:00:00|경제|경제|홍길동 기자|단일 줄 기사 본문입니다.\n"
)

MULTI_LINE_ARTICLE = (
    "조선일보|멀티라인 제목|https://example.com/2|2025-03-01 14:30:00|IT|IT|김기자|"
    '"첫 번째 문단입니다.\n'
    "\n"
    "두 번째 문단입니다.\n"
    "\n"
    '세 번째 문단입니다."\n'
)

TWO_RECORDS = HEADER + SINGLE_LINE_ARTICLE + MULTI_LINE_ARTICLE


def _write_csv(content: str) -> Path:
    f = tempfile.NamedTemporaryFile(mode="w", suffix=".csv", delete=False, encoding="utf-8")
    f.write(content)
    f.close()
    return Path(f.name)


class TestParseCsv:
    def test_parse_single_record(self):
        path = _write_csv(HEADER + SINGLE_LINE_ARTICLE)
        records = list(parse_csv(path))

        assert len(records) == 1
        r = records[0]
        assert r.publisher == "한경"
        assert r.title == "테스트 뉴스 제목"
        assert r.link == "https://example.com/1"
        assert r.published_at == "2025-01-15 09:00:00"
        assert r.reporter == "홍길동 기자"
        assert r.article == "단일 줄 기사 본문입니다."

    def test_parse_multiline_article(self):
        path = _write_csv(HEADER + MULTI_LINE_ARTICLE)
        records = list(parse_csv(path))

        assert len(records) == 1
        r = records[0]
        assert r.publisher == "조선일보"
        assert "첫 번째 문단" in r.article
        assert "세 번째 문단" in r.article

    def test_parse_multiple_records(self):
        path = _write_csv(TWO_RECORDS)
        records = list(parse_csv(path))

        assert len(records) == 2
        assert records[0].publisher == "한경"
        assert records[1].publisher == "조선일보"

    def test_skip_header(self):
        path = _write_csv(TWO_RECORDS)
        records = list(parse_csv(path))
        titles = [r.title for r in records]

        assert "title" not in titles

    def test_empty_optional_fields(self):
        path = _write_csv(HEADER + SINGLE_LINE_ARTICLE)
        records = list(parse_csv(path))
        r = records[0]

        assert r.category is None or r.category == "경제"

    def test_resume_from_offset(self):
        content = HEADER + SINGLE_LINE_ARTICLE + MULTI_LINE_ARTICLE
        path = _write_csv(content)

        offset = len(HEADER.encode("utf-8")) + len(SINGLE_LINE_ARTICLE.encode("utf-8"))
        records = list(parse_csv(path, byte_offset=offset))

        assert len(records) == 1
        assert records[0].publisher == "조선일보"

    def test_yields_newsrecord_dataclass(self):
        path = _write_csv(HEADER + SINGLE_LINE_ARTICLE)
        records = list(parse_csv(path))

        assert isinstance(records[0], NewsRecord)

    def test_empty_file_returns_no_records(self):
        path = _write_csv(HEADER)
        records = list(parse_csv(path))

        assert len(records) == 0
