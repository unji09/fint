from __future__ import annotations

import csv
import io
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class NewsRecord:
    publisher: str
    title: str
    link: str | None
    published_at: str
    category: str | None
    reporter: str | None
    article: str


_FIELD_NAMES = ("company", "title", "link", "published", "category", "category_str", "reporter", "article")


def parse_csv(path: Path, *, byte_offset: int = 0) -> Iterator[NewsRecord]:
    with open(path, encoding="utf-8") as f:
        if byte_offset == 0:
            f.readline()
        else:
            f.buffer.seek(byte_offset)
            remainder = f.buffer.read(4096)
            f.buffer.seek(byte_offset)
            if remainder:
                f.readline()

        reader = csv.reader(f, delimiter="|", quotechar='"')
        for row in reader:
            if len(row) < 8:
                continue

            publisher = row[0].strip()
            title = row[1].strip()
            if not publisher or not title:
                continue

            link = row[2].strip() or None
            published_at = row[3].strip()
            category = row[4].strip() or None
            reporter = row[6].strip() or None
            article = row[7].strip()

            if not published_at or not article:
                continue

            yield NewsRecord(
                publisher=publisher,
                title=title,
                link=link,
                published_at=published_at,
                category=category,
                reporter=reporter,
                article=article,
            )


def get_byte_position(f: io.TextIOBase) -> int:
    return f.buffer.tell()
