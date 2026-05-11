"""multilingual-e5-small 모델을 ONNX로 변환하는 1회성 스크립트.

사용법:
    uv run --group batch python scripts/export_e5_onnx.py [--output-dir model/e5-small]

사전 조건:
    uv sync --group batch  (torch, transformers, sentence-transformers 설치)
    ONNX 변환 시 transformers 4.44를 사용해야 TorchScript 호환.
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

MODEL_NAME = "intfloat/multilingual-e5-small"


def main() -> None:
    parser = argparse.ArgumentParser(description="Export multilingual-e5-small to ONNX")
    parser.add_argument("--output-dir", type=str, default="model/e5-small")
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading model: {MODEL_NAME}")
    import torch
    from transformers import AutoModel, AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME)
    model.eval()

    dummy = tokenizer(
        "query: test", return_tensors="pt", padding=True, truncation=True, max_length=512
    )

    onnx_path = output_dir / "model.onnx"
    print(f"Exporting to ONNX: {onnx_path}")

    with torch.no_grad():
        torch.onnx.export(
            model,
            (dummy["input_ids"], dummy["attention_mask"]),
            str(onnx_path),
            input_names=["input_ids", "attention_mask"],
            output_names=["last_hidden_state"],
            dynamic_axes={
                "input_ids": {0: "batch", 1: "seq"},
                "attention_mask": {0: "batch", 1: "seq"},
                "last_hidden_state": {0: "batch", 1: "seq"},
            },
            opset_version=14,
            dynamo=False,
        )

    tokenizer.save_pretrained(str(output_dir))

    if not (output_dir / "tokenizer.json").exists():
        raise FileNotFoundError("tokenizer.json not created")

    keep = {"model.onnx", "tokenizer.json"}
    for f in list(output_dir.iterdir()):
        if f.name not in keep:
            if f.is_file():
                f.unlink()
            elif f.is_dir():
                shutil.rmtree(f)

    onnx_size = (output_dir / "model.onnx").stat().st_size / (1024 * 1024)
    print(f"\nDone. Files in {output_dir}:")
    for f in sorted(output_dir.iterdir()):
        size_mb = f.stat().st_size / (1024 * 1024)
        print(f"  {f.name}: {size_mb:.1f} MB")

    if onnx_size < 50:
        print(f"\nWARNING: model.onnx is only {onnx_size:.1f} MB (expected ~120 MB)")


if __name__ == "__main__":
    main()
