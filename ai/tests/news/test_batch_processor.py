from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from app.news.batch_processor import BatchProcessor
from app.news.checkpoint import Checkpoint, load_checkpoint

HEADER = "company|title|link|published|category|category_str|reporter|article\n"

RECORD_1 = "한경|제목1|https://ex.com/1|2025-01-01 09:00:00|경제||기자1|기사 본문 1\n"
RECORD_2 = "조선|제목2|https://ex.com/2|2025-01-02 10:00:00|IT||기자2|기사 본문 2\n"
RECORD_3 = "매경|제목3|https://ex.com/3|2025-01-03 11:00:00|사회||기자3|기사 본문 3\n"


class StubSummarizer:
    def summarize_batch(self, texts: list[str]) -> list[str]:
        return [f"요약: {t[:10]}" for t in texts]


class StubEmbedder:
    def embed_passages(self, texts: list[str]) -> np.ndarray:
        return np.random.default_rng(0).standard_normal((len(texts), 384)).astype(np.float32)

    @property
    def dimension(self) -> int:
        return 384


@pytest.fixture
def csv_dir(tmp_path: Path) -> Path:
    f1 = tmp_path / "news_2025.csv"
    f1.write_text(HEADER + RECORD_1 + RECORD_2 + RECORD_3, encoding="utf-8")
    return tmp_path


@pytest.fixture
def processor(csv_dir: Path, tmp_path: Path) -> BatchProcessor:
    return BatchProcessor(
        csv_files=[csv_dir / "news_2025.csv"],
        checkpoint_path=tmp_path / "cp.json",
        db_url="postgresql+asyncpg://fint:fint@localhost:5432/fint_local",
        summarizer=StubSummarizer(),
        embedder=StubEmbedder(),
        batch_size=2,
    )


class TestBatchProcessor:
    def test_init(self, processor: BatchProcessor):
        assert processor._batch_size == 2
        assert len(processor._csv_files) == 1

    @pytest.mark.asyncio
    async def test_process_file_collects_records(self, processor: BatchProcessor):
        records = []
        async for batch, _offset in processor._iter_batches(processor._csv_files[0], byte_offset=0):
            records.extend(batch)

        assert len(records) == 3

    @pytest.mark.asyncio
    async def test_iter_batches_respects_batch_size(self, processor: BatchProcessor):
        batches = []
        async for batch, _offset in processor._iter_batches(processor._csv_files[0], byte_offset=0):
            batches.append(batch)

        assert len(batches) == 2
        assert len(batches[0]) == 2
        assert len(batches[1]) == 1

    @pytest.mark.asyncio
    async def test_iter_batches_returns_byte_offset(self, processor: BatchProcessor):
        offsets = []
        async for _batch, offset in processor._iter_batches(processor._csv_files[0], byte_offset=0):
            offsets.append(offset)

        assert len(offsets) == 2
        assert offsets[0] > 0
        assert offsets[1] >= offsets[0]

    def test_prepare_batch_creates_summaries_and_embeddings(self, processor: BatchProcessor):
        from app.news.csv_parser import NewsRecord

        records = [
            NewsRecord("한경", "제목1", None, "2025-01-01 09:00:00", None, None, "기사 본문 1"),
            NewsRecord("조선", "제목2", None, "2025-01-02 10:00:00", None, None, "기사 본문 2"),
        ]
        prepared = processor._prepare_batch(records)

        assert len(prepared) == 2
        assert prepared[0]["content_summary"].startswith("요약:")
        assert prepared[0]["title_embedding"].shape == (384,)
        assert prepared[0]["summary_embedding"].shape == (384,)

    def test_checkpoint_saved_after_processing(self, processor: BatchProcessor, tmp_path: Path):
        cp = Checkpoint(
            completed_files=[],
            current_file="news_2025.csv",
            records_processed=100,
            byte_offset=5000,
        )
        from app.news.checkpoint import save_checkpoint

        save_checkpoint(cp, tmp_path / "cp.json")
        loaded = load_checkpoint(tmp_path / "cp.json")

        assert loaded.records_processed == 100
        assert loaded.byte_offset == 5000
