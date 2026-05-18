"""Append-only feedback storage (JSONL)."""
from __future__ import annotations

import json
import os
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

_DEFAULT_PATH = Path(__file__).parent / "data" / "feedback.jsonl"
FEEDBACK_PATH = Path(os.environ.get("FEEDBACK_PATH") or _DEFAULT_PATH)


def save_feedback(
    features: dict,
    recommendation: dict,
    label: str,
    *,
    input_text: Optional[str] = None,
    label_reason: Optional[str] = None,
    session_id: Optional[str] = None,
) -> None:
    if label not in ("good", "bad", "neutral"):
        raise ValueError(f"label must be good/bad/neutral, got {label!r}")

    clean_feats = {k: v for k, v in features.items() if not k.startswith("__")}

    record = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "input_text": input_text,
        "features": clean_feats,
        "recommendation": recommendation,
        "label": label,
        "label_reason": label_reason,
        "session_id": session_id,
    }
    FEEDBACK_PATH.parent.mkdir(parents=True, exist_ok=True)
    with FEEDBACK_PATH.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, ensure_ascii=False) + "\n")
    logger.info("Feedback saved: action=%s label=%s", recommendation.get("id"), label)


def load_all() -> list[dict]:
    if not FEEDBACK_PATH.exists():
        return []
    out = []
    with FEEDBACK_PATH.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out


def summary() -> dict:
    records = load_all()
    if not records:
        return {"total": 0}
    labels = [r["label"] for r in records]
    return {
        "total": len(records),
        "good": labels.count("good"),
        "bad": labels.count("bad"),
        "neutral": labels.count("neutral"),
        "unique_actions": len(set(r["recommendation"]["id"] for r in records)),
    }
