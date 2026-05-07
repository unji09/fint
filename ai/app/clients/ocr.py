from __future__ import annotations

import asyncio
import threading
from dataclasses import dataclass
from functools import partial
from typing import TYPE_CHECKING, Any, Protocol, runtime_checkable

from app.core.errors import BusinessException, CommonErrorCode

if TYPE_CHECKING:
    import numpy as np


@dataclass(frozen=True)
class OcrBox:
    text: str
    box: tuple[tuple[float, float], tuple[float, float], tuple[float, float], tuple[float, float]]
    font_height: float
    confidence: float = 1.0


@runtime_checkable
class OcrClient(Protocol):
    async def recognize(self, image_bytes: bytes) -> list[OcrBox]: ...


class RapidOcrClient:
    def __init__(self) -> None:
        self._engine: Any | None = None
        self._engine_lock = threading.Lock()

    def _get_engine(self) -> Any:
        if self._engine is not None:
            return self._engine
        with self._engine_lock:
            if self._engine is None:
                import logging

                from rapidocr import LangDet, LangRec, ModelType, OCRVersion, RapidOCR  # type: ignore[import-untyped]

                _rapidocr_logger = logging.getLogger("RapidOCR")
                _rapidocr_logger.setLevel(logging.WARNING)
                for _h in list(_rapidocr_logger.handlers):
                    _h.setLevel(logging.WARNING)
                self._engine = RapidOCR(params={
                    "Global.text_score": 0.4,
                    "Det.lang_type": LangDet.CH,
                    "Det.ocr_version": OCRVersion.PPOCRV5,
                    "Det.model_type": ModelType.MOBILE,
                    "Det.box_thresh": 0.4,
                    "Rec.lang_type": LangRec.KOREAN,
                    "Rec.ocr_version": OCRVersion.PPOCRV5,
                    "Rec.model_type": ModelType.MOBILE,
                })
            return self._engine

    async def warmup(self) -> None:
        await asyncio.to_thread(self._get_engine)

    async def recognize(self, image_bytes: bytes) -> list[OcrBox]:
        try:
            engine = self._get_engine()
            return await asyncio.to_thread(partial(_run_inference, engine, image_bytes))
        except BusinessException:
            raise
        except Exception as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED, f"OCR inference failed: {e}"
            ) from e


def _preprocess_for_ocr(arr: np.ndarray) -> np.ndarray:
    import cv2
    import numpy as np

    h, w = arr.shape[:2]
    short_side = min(h, w)
    if short_side < 1500:
        scale = 2.0 if short_side < 900 else 1.5
        arr = cv2.resize(arr, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_CUBIC)

    gray = cv2.cvtColor(arr, cv2.COLOR_RGB2GRAY)

    kernel_size = max(31, min(gray.shape) // 30 | 1)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    background = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, kernel)
    shadow_free = cv2.divide(gray, background, scale=255).astype(np.uint8)

    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(shadow_free)
    filtered = cv2.bilateralFilter(enhanced, d=5, sigmaColor=40, sigmaSpace=40)
    return cv2.cvtColor(filtered, cv2.COLOR_GRAY2RGB)


def _run_inference(engine: Any, image_bytes: bytes) -> list[OcrBox]:
    import io

    import numpy as np
    from PIL import Image

    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    arr = np.array(image)
    arr = _preprocess_for_ocr(arr)
    result = engine(arr)

    boxes_attr = getattr(result, "boxes", None)
    txts_attr = getattr(result, "txts", None)
    scores_attr = getattr(result, "scores", None) or ()
    if boxes_attr is None or txts_attr is None or len(txts_attr) == 0:
        return []

    boxes: list[OcrBox] = []
    for idx, (poly, text) in enumerate(zip(boxes_attr, txts_attr)):
        (x1, y1), (x2, y2), (x3, y3), (x4, y4) = poly
        font_height = (abs(float(y4) - float(y1)) + abs(float(y3) - float(y2))) / 2.0
        confidence = float(scores_attr[idx]) if idx < len(scores_attr) else 1.0
        boxes.append(
            OcrBox(
                text=str(text),
                box=(
                    (float(x1), float(y1)),
                    (float(x2), float(y2)),
                    (float(x3), float(y3)),
                    (float(x4), float(y4)),
                ),
                font_height=font_height,
                confidence=confidence,
            )
        )
    return boxes
