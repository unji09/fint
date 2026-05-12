"""Load actions catalog and feature schema from JSON files."""
import json
from pathlib import Path

_DATA_DIR = Path(__file__).parent / "data"


def load_actions() -> dict[str, dict]:
    with open(_DATA_DIR / "actions.json", encoding="utf-8") as f:
        data = json.load(f)
    return {a["id"]: a for a in data["actions"]}


def load_features() -> dict[str, dict]:
    with open(_DATA_DIR / "features.json", encoding="utf-8") as f:
        data = json.load(f)
    return {feat["id"]: feat for feat in data["features"]}


ACTIONS: dict[str, dict] = load_actions()
FEATURES: dict[str, dict] = load_features()
