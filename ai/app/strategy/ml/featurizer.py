"""Convert (features_dict, action_id) -> numeric feature vector for ML.

Encoding:
  - boolean       -> 0/1 with null=-1
  - categorical   -> one-hot (N+1 columns: each value + "unknown")
  - multi_select  -> multi-hot (N columns)
  - numeric       -> as-is with null=-1
  - action_id     -> one-hot over all ACT_*
"""
from __future__ import annotations

from pathlib import Path

import numpy as np

from ..data import ACTIONS, FEATURES

_ACTIVE_FEATURES: list[dict] = [
    f for f in sorted(FEATURES.values(), key=lambda x: x["id"])
    if not f.get("deprecated")
]
_ACTION_IDS: list[str] = sorted(ACTIONS.keys())


def _build_column_names() -> list[str]:
    cols: list[str] = []
    for feat in _ACTIVE_FEATURES:
        fid = feat["id"]
        t = feat["type"]
        if t == "boolean":
            cols.append(f"{fid}=bool")
        elif t == "categorical":
            for v in feat["values"]:
                cols.append(f"{fid}={v}")
            cols.append(f"{fid}=__unknown__")
        elif t == "multi_select":
            for v in feat["values"]:
                cols.append(f"{fid}={v}")
        elif t == "numeric":
            cols.append(f"{fid}=num")
    for aid in _ACTION_IDS:
        cols.append(f"action={aid}")
    return cols


COLUMNS = _build_column_names()
N_FEAT_COLS = len(COLUMNS) - len(_ACTION_IDS)
N_COLS = len(COLUMNS)


def featurize(features: dict, action_id: str) -> np.ndarray:
    vec = np.zeros(N_COLS, dtype=np.float32)
    idx = 0
    for feat in _ACTIVE_FEATURES:
        fid = feat["id"]
        t = feat["type"]
        val = features.get(fid)
        if t == "boolean":
            vec[idx] = 1.0 if val is True else (0.0 if val is False else -1.0)
            idx += 1
        elif t == "categorical":
            matched = False
            for v in feat["values"]:
                if val == v:
                    vec[idx] = 1.0
                    matched = True
                idx += 1
            if not matched:
                vec[idx] = 1.0
            idx += 1
        elif t == "multi_select":
            cur = set(val) if isinstance(val, list) else set()
            for v in feat["values"]:
                vec[idx] = 1.0 if v in cur else 0.0
                idx += 1
        elif t == "numeric":
            vec[idx] = float(val) if isinstance(val, (int, float)) else -1.0
            idx += 1

    if action_id in _ACTION_IDS:
        action_idx = N_FEAT_COLS + _ACTION_IDS.index(action_id)
        vec[action_idx] = 1.0

    return vec


def featurize_for_inference(features: dict) -> tuple[np.ndarray, list[str]]:
    """For predicting scores for ALL actions given a feature dict."""
    X = np.zeros((len(_ACTION_IDS), N_COLS), dtype=np.float32)
    for i, aid in enumerate(_ACTION_IDS):
        X[i] = featurize(features, aid)
    return X, _ACTION_IDS
