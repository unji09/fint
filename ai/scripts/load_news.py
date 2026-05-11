"""뉴스 CSV 배치 적재 CLI.

사용법:
    uv run --group batch python scripts/load_news.py \
        --csv-dir /path/to/csv \
        --db-url "postgresql+asyncpg://fint:fint@localhost:5432/fint_local" \
        --model-dir model/e5-small \
        [--batch-size 512] \
        [--checkpoint checkpoint.json] \
        [--years 2024,2025]

CSV 파일 네이밍 규칙:
    ssafy_dataset_news_2025_1st_half.csv
    ssafy_dataset_news_2024_1st_half.csv
    ssafy_dataset_news_2024_2nd_half.csv
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import time
from pathlib import Path

import numpy as np

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s — %(message)s")
logger = logging.getLogger(__name__)


def _find_csv_files(csv_dir: Path, years: list[str] | None) -> list[Path]:
    files = sorted(csv_dir.glob("ssafy_dataset_news_*.csv"))
    if years:
        files = [f for f in files if any(y in f.name for y in years)]
    return files


class KoBARTSummarizer:
    def __init__(self) -> None:
        from transformers import PreTrainedModel, PreTrainedTokenizerFast

        logger.info("Loading KoBART summarization model...")
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

        self._tokenizer: PreTrainedTokenizerFast = AutoTokenizer.from_pretrained("digit82/kobart-summarization")
        self._model: PreTrainedModel = AutoModelForSeq2SeqLM.from_pretrained("digit82/kobart-summarization")

        import torch

        if torch.cuda.is_available():
            self._model = self._model.to("cuda")
            logger.info("KoBART loaded on GPU")
        else:
            logger.info("KoBART loaded on CPU (slow)")
        self._model.eval()

    def summarize_batch(self, texts: list[str], sub_batch_size: int = 8) -> list[str]:
        import torch

        device = next(self._model.parameters()).device
        max_input_len = 1024
        all_summaries: list[str] = []

        for i in range(0, len(texts), sub_batch_size):
            chunk = texts[i : i + sub_batch_size]
            inputs = self._tokenizer(
                chunk,
                max_length=max_input_len,
                truncation=True,
                padding=True,
                return_tensors="pt",
            ).to(device)

            with torch.no_grad():
                outputs = self._model.generate(
                    **inputs,
                    max_new_tokens=150,
                    num_beams=1,
                    do_sample=False,
                )

            all_summaries.extend(self._tokenizer.batch_decode(outputs, skip_special_tokens=True))

        return all_summaries


def main() -> None:
    parser = argparse.ArgumentParser(description="Load news CSV into PostgreSQL with embeddings")
    parser.add_argument("--csv-dir", type=str, required=True)
    parser.add_argument("--db-url", type=str, required=True)
    parser.add_argument("--model-dir", type=str, default="model/e5-small")
    parser.add_argument("--batch-size", type=int, default=512)
    parser.add_argument("--checkpoint", type=str, default="checkpoint.json")
    parser.add_argument("--years", type=str, default=None, help="Comma-separated years, e.g. 2024,2025")
    args = parser.parse_args()

    csv_dir = Path(args.csv_dir)
    csv_files = _find_csv_files(csv_dir, args.years.split(",") if args.years else None)

    if not csv_files:
        logger.error("No CSV files found in %s", csv_dir)
        return

    logger.info("Found %d CSV files: %s", len(csv_files), [f.name for f in csv_files])

    from app.clients.embedder import OnnxEmbedderClient

    logger.info("Loading e5-small ONNX embedder (CPU)...")
    embedder = OnnxEmbedderClient(args.model_dir)
    logger.info("Embedder loaded (dim=%d)", embedder.dimension)

    summarizer = KoBARTSummarizer()

    from app.news.batch_processor import BatchProcessor

    processor = BatchProcessor(
        csv_files=csv_files,
        checkpoint_path=Path(args.checkpoint),
        db_url=args.db_url,
        summarizer=summarizer,
        embedder=embedder,
        batch_size=args.batch_size,
    )

    start = time.time()
    result = asyncio.run(processor.run())
    elapsed = time.time() - start

    logger.info("Done in %.1f seconds. Inserted: %d", elapsed, result["total_inserted"])


if __name__ == "__main__":
    main()
