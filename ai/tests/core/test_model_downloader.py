"""ensure_embedding_model 자동 다운로드 로직 테스트."""
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from app.core.model_downloader import ensure_embedding_model


@pytest.fixture
def model_dir(tmp_path: Path) -> Path:
    return tmp_path / "model" / "e5-small"


class TestEnsureEmbeddingModel:
    def test_skips_download_when_files_exist(self, model_dir: Path):
        model_dir.mkdir(parents=True)
        (model_dir / "model.onnx").write_bytes(b"fake-onnx")
        (model_dir / "tokenizer.json").write_text("{}")

        with patch("app.core.model_downloader.hf_hub_download") as mock_dl:
            result = ensure_embedding_model(model_dir)

        assert result is True
        mock_dl.assert_not_called()

    def test_downloads_when_files_missing(self, model_dir: Path):
        def fake_download(*, repo_id, filename, local_dir, **kwargs):
            dest = Path(local_dir) / filename
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(b"fake")
            return str(dest)

        with patch(
            "app.core.model_downloader.hf_hub_download",
            side_effect=fake_download,
        ) as mock_dl:
            result = ensure_embedding_model(model_dir)

        assert result is True
        assert (model_dir / "model.onnx").exists()
        assert (model_dir / "tokenizer.json").exists()
        assert not (model_dir / "onnx").exists()
        assert mock_dl.call_count == 2

    def test_returns_false_on_download_failure(self, model_dir: Path):
        with patch(
            "app.core.model_downloader.hf_hub_download",
            side_effect=OSError("network error"),
        ):
            result = ensure_embedding_model(model_dir)

        assert result is False

    def test_skips_when_only_onnx_missing(self, model_dir: Path):
        model_dir.mkdir(parents=True)
        (model_dir / "tokenizer.json").write_text("{}")

        def fake_download(*, repo_id, filename, local_dir, **kwargs):
            dest = Path(local_dir) / filename
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(b"fake")
            return str(dest)

        with patch(
            "app.core.model_downloader.hf_hub_download",
            side_effect=fake_download,
        ) as mock_dl:
            result = ensure_embedding_model(model_dir)

        assert result is True
        assert mock_dl.call_count == 2
