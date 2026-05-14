from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from datetime import datetime
from pathlib import Path
from typing import Any, Protocol

import numpy as np

from app.news.checkpoint import load_checkpoint, save_checkpoint
from app.news.csv_parser import NewsRecord

logger = logging.getLogger(__name__)

CHECKPOINT_INTERVAL = 1_000
LOG_INTERVAL = 100


class SummarizerProtocol(Protocol):
    def summarize_batch(self, texts: list[str]) -> list[str]: ...


class EmbedderProtocol(Protocol):
    def embed_passages(self, texts: list[str]) -> np.ndarray: ...

    @property
    def dimension(self) -> int: ...


class BatchProcessor:
    def __init__(
        self,
        *,
        csv_files: list[Path],
        checkpoint_path: Path,
        db_url: str,
        summarizer: SummarizerProtocol,
        embedder: EmbedderProtocol,
        batch_size: int = 512,
    ) -> None:
        self._csv_files = csv_files
        self._checkpoint_path = checkpoint_path
        self._db_url = db_url
        self._summarizer = summarizer
        self._embedder = embedder
        self._batch_size = batch_size

    async def run(self) -> dict[str, int]:
        import asyncpg

        cp = load_checkpoint(self._checkpoint_path)
        total_inserted = 0

        conn = await asyncpg.connect(self._db_url.replace("+asyncpg", ""))
        try:
            for csv_file in self._csv_files:
                filename = csv_file.name

                if cp.is_file_completed(filename):
                    logger.info("Skipping completed file: %s", filename)
                    continue

                byte_offset = cp.byte_offset if cp.current_file == filename else 0
                records_base = cp.records_processed if cp.current_file == filename else 0

                cp.current_file = filename
                records_in_file = records_base

                total_failed = 0
                async for batch, new_offset in self._iter_batches(csv_file, byte_offset=byte_offset):
                    prepared = self._prepare_batch(batch)
                    inserted = await self._insert_batch(prepared, conn)
                    total_inserted += inserted
                    batch_failed = len(batch) - inserted
                    total_failed += batch_failed
                    records_in_file += len(batch)

                    logger.info(
                        "[%s] %d / 640,857 | batch: %d건 (성공 %d, 스킵 %d) | 누적: 성공 %d, 스킵 %d",
                        filename, records_in_file, len(batch), inserted, batch_failed,
                        total_inserted, total_failed,
                    )

                    if records_in_file % CHECKPOINT_INTERVAL < self._batch_size:
                        cp.records_processed = records_in_file
                        cp.byte_offset = new_offset
                        save_checkpoint(cp, self._checkpoint_path)
                        logger.info("[%s] === CHECKPOINT saved at %d ===", filename, records_in_file)

                cp.completed_files.append(filename)
                cp.current_file = None
                cp.records_processed = 0
                cp.byte_offset = 0
                save_checkpoint(cp, self._checkpoint_path)
                logger.info("Completed file: %s (%d records)", filename, records_in_file)
        finally:
            await conn.close()

        return {"total_inserted": total_inserted}

    async def _iter_batches(
        self, csv_file: Path, *, byte_offset: int = 0
    ) -> AsyncIterator[tuple[list[NewsRecord], int]]:
        batch: list[NewsRecord] = []
        with open(csv_file, "rb") as raw:
            if byte_offset == 0:
                raw.readline()
            else:
                raw.seek(byte_offset)
                raw.readline()

            import io

            f = io.TextIOWrapper(raw, encoding="utf-8")

            import csv as csv_mod

            reader = csv_mod.reader(f, delimiter="|", quotechar='"')
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

                batch.append(
                    NewsRecord(
                        publisher=publisher,
                        title=title,
                        link=link,
                        published_at=published_at,
                        category=category,
                        reporter=reporter,
                        article=article,
                    )
                )
                if len(batch) >= self._batch_size:
                    yield batch, f.buffer.tell()
                    batch = []
            if batch:
                yield batch, f.buffer.tell()

    def _prepare_batch(self, records: list[NewsRecord]) -> list[dict[str, Any]]:
        articles = [r.article for r in records]
        summaries = self._summarizer.summarize_batch(articles)

        title_embeddings = self._embedder.embed_passages([r.title for r in records])
        summary_embeddings = self._embedder.embed_passages(summaries)

        prepared = []
        for i, record in enumerate(records):
            prepared.append(
                {
                    "publisher": record.publisher,
                    "title": record.title,
                    "link": record.link,
                    "published_at": _parse_datetime(record.published_at),
                    "category": record.category,
                    "reporter": record.reporter,
                    "article": record.article,
                    "content_summary": summaries[i],
                    "title_embedding": title_embeddings[i],
                    "summary_embedding": summary_embeddings[i],
                }
            )
        return prepared

    async def _insert_batch(self, rows: list[dict[str, Any]], conn: Any) -> int:
        inserted = 0
        for row in rows:
            try:
                result = await conn.execute(
                    """
                    INSERT INTO news_articles
                        (publisher, title, link, published_at, category, reporter,
                         article, content_summary, title_embedding, summary_embedding)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
                    ON CONFLICT (publisher, title, published_at) DO NOTHING
                    """,
                    row["publisher"],
                    row["title"],
                    row["link"],
                    row["published_at"],
                    row["category"],
                    row["reporter"],
                    row["article"],
                    row["content_summary"],
                    _vec_to_pgvector(row["title_embedding"]),
                    _vec_to_pgvector(row["summary_embedding"]),
                )
                if result and result.endswith("1"):
                    inserted += 1
            except Exception:
                logger.warning("Failed to insert: %s - %s", row["publisher"], row["title"], exc_info=True)
        return inserted


_DT_FORMATS = ["%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d"]


def _parse_datetime(s: str) -> datetime:
    for fmt in _DT_FORMATS:
        try:
            return datetime.strptime(s, fmt)
        except ValueError:
            continue
    raise ValueError(f"Cannot parse datetime: {s!r}")


def _vec_to_pgvector(vec: np.ndarray) -> str:
    return "[" + ",".join(f"{v:.6f}" for v in vec.tolist()) + "]"
