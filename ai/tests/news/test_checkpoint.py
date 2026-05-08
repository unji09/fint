from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.news.checkpoint import Checkpoint, load_checkpoint, save_checkpoint


@pytest.fixture
def cp_path(tmp_path: Path) -> Path:
    return tmp_path / "checkpoint.json"


class TestCheckpoint:
    def test_save_and_load(self, cp_path: Path):
        cp = Checkpoint(
            completed_files=["2023.csv"],
            current_file="2024.csv",
            records_processed=50000,
            byte_offset=12345678,
        )
        save_checkpoint(cp, cp_path)
        loaded = load_checkpoint(cp_path)

        assert loaded.completed_files == ["2023.csv"]
        assert loaded.current_file == "2024.csv"
        assert loaded.records_processed == 50000
        assert loaded.byte_offset == 12345678

    def test_load_nonexistent_returns_empty(self, cp_path: Path):
        loaded = load_checkpoint(cp_path)

        assert loaded.completed_files == []
        assert loaded.current_file is None
        assert loaded.records_processed == 0
        assert loaded.byte_offset == 0

    def test_save_overwrites(self, cp_path: Path):
        cp1 = Checkpoint(completed_files=[], current_file="a.csv", records_processed=100, byte_offset=0)
        save_checkpoint(cp1, cp_path)

        cp2 = Checkpoint(completed_files=["a.csv"], current_file="b.csv", records_processed=200, byte_offset=999)
        save_checkpoint(cp2, cp_path)

        loaded = load_checkpoint(cp_path)
        assert loaded.current_file == "b.csv"
        assert loaded.records_processed == 200

    def test_checkpoint_file_is_valid_json(self, cp_path: Path):
        cp = Checkpoint(completed_files=[], current_file="test.csv", records_processed=0, byte_offset=0)
        save_checkpoint(cp, cp_path)

        data = json.loads(cp_path.read_text(encoding="utf-8"))
        assert "current_file" in data

    def test_is_file_completed(self, cp_path: Path):
        cp = Checkpoint(
            completed_files=["2023.csv", "2024_h1.csv"],
            current_file=None,
            records_processed=0,
            byte_offset=0,
        )
        assert cp.is_file_completed("2023.csv")
        assert not cp.is_file_completed("2025.csv")
