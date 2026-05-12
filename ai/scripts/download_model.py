"""intfloat/multilingual-e5-small ONNX 모델을 HuggingFace에서 직접 다운로드."""
import os

from huggingface_hub import hf_hub_download

REPO_ID = "intfloat/multilingual-e5-small"
SAVE_DIR = "model/e5-small"

FILES = [
    "onnx/model.onnx",
    "onnx/tokenizer.json",
]


def main():
    os.makedirs(SAVE_DIR, exist_ok=True)

    for remote_path in FILES:
        filename = os.path.basename(remote_path)
        print(f"Downloading {remote_path}...")
        hf_hub_download(
            repo_id=REPO_ID,
            filename=remote_path,
            local_dir=SAVE_DIR,
            local_dir_use_symlinks=False,
        )
        src = os.path.join(SAVE_DIR, remote_path)
        dst = os.path.join(SAVE_DIR, filename)
        if src != dst and os.path.exists(src):
            os.replace(src, dst)

    onnx_dir = os.path.join(SAVE_DIR, "onnx")
    if os.path.isdir(onnx_dir):
        for f in os.listdir(onnx_dir):
            os.remove(os.path.join(onnx_dir, f))
        os.rmdir(onnx_dir)

    print("Done!")
    print("Files:", os.listdir(SAVE_DIR))


if __name__ == "__main__":
    main()
