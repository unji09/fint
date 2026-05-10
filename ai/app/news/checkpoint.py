from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Checkpoint:
    completed_files: list[str] = field(default_factory=list)
    current_file: str | None = None
    records_processed: int = 0
    byte_offset: int = 0

    def is_file_completed(self, filename: str) -> bool:
        return filename in self.completed_files


def save_checkpoint(cp: Checkpoint, path: Path) -> None:
    data = {
        "completed_files": cp.completed_files,
        "current_file": cp.current_file,
        "records_processed": cp.records_processed,
        "byte_offset": cp.byte_offset,
    }
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def load_checkpoint(path: Path) -> Checkpoint:
    if not path.exists():
        return Checkpoint()
    data = json.loads(path.read_text(encoding="utf-8"))
    return Checkpoint(
        completed_files=data.get("completed_files", []),
        current_file=data.get("current_file"),
        records_processed=data.get("records_processed", 0),
        byte_offset=data.get("byte_offset", 0),
    )
