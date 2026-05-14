"""Embedding 모델 자동 다운로드 — HuggingFace Hub에서 ONNX 모델을 가져온다."""
from __future__ import annotations

import logging
from pathlib import Path

from huggingface_hub import hf_hub_download

logger = logging.getLogger(__name__)

REPO_ID = "intfloat/multilingual-e5-small"
_REMOTE_FILES = ["onnx/model.onnx", "onnx/tokenizer.json"]


def ensure_embedding_model(model_dir: str | Path) -> bool:
    model_path = Path(model_dir)

    if (model_path / "model.onnx").exists() and (model_path / "tokenizer.json").exists():
        logger.info("Embedding model already present at %s", model_path)
        return True

    logger.info("Downloading embedding model from %s to %s …", REPO_ID, model_path)
    try:
        model_path.mkdir(parents=True, exist_ok=True)

        for remote_path in _REMOTE_FILES:
            local_name = Path(remote_path).name
            hf_hub_download(
                repo_id=REPO_ID,
                filename=remote_path,
                local_dir=str(model_path),
                local_dir_use_symlinks=False,
            )
            src = model_path / remote_path
            dst = model_path / local_name
            if src != dst and src.exists():
                dst.write_bytes(src.read_bytes())
                src.unlink()

        onnx_dir = model_path / "onnx"
        if onnx_dir.is_dir():
            for f in onnx_dir.iterdir():
                f.unlink()
            onnx_dir.rmdir()

        logger.info("Embedding model ready at %s", model_path)
        return True
    except Exception:
        logger.exception("Failed to download embedding model from %s", REPO_ID)
        return False
