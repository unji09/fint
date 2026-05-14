from __future__ import annotations

import logging
from pathlib import Path
from typing import Protocol, runtime_checkable

import numpy as np

logger = logging.getLogger(__name__)

_EMBED_BATCH_SIZE = 32


@runtime_checkable
class EmbedderClient(Protocol):
    @property
    def dimension(self) -> int: ...
    def embed(self, texts: list[str]) -> np.ndarray: ...
    def embed_query(self, text: str) -> np.ndarray: ...
    def embed_passages(self, texts: list[str]) -> np.ndarray: ...


class OnnxEmbedderClient:
    def __init__(self, model_dir: str | Path) -> None:
        import onnxruntime as ort
        from tokenizers import Tokenizer

        model_path = Path(model_dir)
        self._session = ort.InferenceSession(
            str(model_path / "model.onnx"),
            providers=["CPUExecutionProvider"],
        )
        self._tokenizer = Tokenizer.from_file(str(model_path / "tokenizer.json"))
        self._tokenizer.enable_padding()
        self._tokenizer.enable_truncation(max_length=512)
        self._dim = self._session.get_outputs()[0].shape[-1]

    @property
    def dimension(self) -> int:
        return self._dim

    def embed(self, texts: list[str]) -> np.ndarray:
        if not texts:
            return np.empty((0, self._dim), dtype=np.float32)

        model_inputs = {i.name for i in self._session.get_inputs()}
        chunks: list[np.ndarray] = []

        for start in range(0, len(texts), _EMBED_BATCH_SIZE):
            batch = texts[start : start + _EMBED_BATCH_SIZE]
            chunks.append(self._embed_chunk(batch, model_inputs))

        return np.concatenate(chunks, axis=0)

    def _embed_chunk(self, texts: list[str], model_inputs: set[str]) -> np.ndarray:
        encodings = self._tokenizer.encode_batch(texts)
        input_ids = np.array([e.ids for e in encodings], dtype=np.int64)
        attention_mask = np.array([e.attention_mask for e in encodings], dtype=np.int64)

        feed = {"input_ids": input_ids, "attention_mask": attention_mask}
        if "token_type_ids" in model_inputs:
            feed["token_type_ids"] = np.zeros_like(input_ids)

        outputs = self._session.run(None, feed)
        token_embeddings = outputs[0]

        mask_expanded = attention_mask[:, :, np.newaxis].astype(np.float32)
        summed = (token_embeddings * mask_expanded).sum(axis=1)
        counts = mask_expanded.sum(axis=1).clip(min=1e-9)
        pooled = summed / counts

        norms = np.linalg.norm(pooled, axis=1, keepdims=True).clip(min=1e-9)
        return (pooled / norms).astype(np.float32)

    def embed_query(self, text: str) -> np.ndarray:
        return self.embed([f"query: {text}"])[0]

    def embed_passages(self, texts: list[str]) -> np.ndarray:
        return self.embed([f"passage: {t}" for t in texts])
