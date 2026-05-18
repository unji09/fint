"""ML scoring backend — predicts {action_id: score} from features.

Same interface as weights.compute_scores so it can be swapped in.
Requires lightgbm; raises ImportError with instructions if missing.
"""
from __future__ import annotations

from pathlib import Path

_MODEL_DIR = Path(__file__).parent
MODEL_DISTILLED_PATH = _MODEL_DIR / "model_distilled.txt"

_MODEL_DISTILLED_CACHE = None


def _load_distilled_model():
    global _MODEL_DISTILLED_CACHE
    if _MODEL_DISTILLED_CACHE is None:
        try:
            import lightgbm as lgb
        except ImportError as e:
            raise ImportError(
                "lightgbm is required for ML backend. Install: pip install lightgbm"
            ) from e

        if not MODEL_DISTILLED_PATH.exists():
            raise FileNotFoundError(
                f"Distilled model not found at {MODEL_DISTILLED_PATH}. "
                "Train the model first or use backend='rule'."
            )
        _MODEL_DISTILLED_CACHE = lgb.Booster(model_file=str(MODEL_DISTILLED_PATH))
    return _MODEL_DISTILLED_CACHE


def compute_scores_ml_distilled(features: dict) -> dict[str, float]:
    """Distilled ML scoring. Same interface as weights.compute_scores."""
    from .featurizer import featurize_for_inference

    model = _load_distilled_model()
    X, action_ids = featurize_for_inference(features)
    preds = model.predict(X)
    return dict(zip(action_ids, [float(p) for p in preds]))
