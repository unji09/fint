import io
from types import SimpleNamespace

import numpy as np
import pytest
from PIL import Image

from app.clients import get_ocr_client
from app.clients.ocr import OcrBox, OcrClient, RapidOcrClient, _run_inference
from app.core.errors import BusinessException


def _png_bytes(width: int = 300, height: int = 100) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (width, height), color="white").save(buf, format="PNG")
    return buf.getvalue()


def test_get_ocr_client_returns_rapidocr():
    assert isinstance(get_ocr_client(), RapidOcrClient)


def test_rapidocr_client_satisfies_protocol():
    client = RapidOcrClient()
    assert isinstance(client, OcrClient)


def test_rapidocr_client_engine_is_lazy():
    client = RapidOcrClient()
    assert client._engine is None


@pytest.mark.asyncio
async def test_rapidocr_recognize_wraps_inference_failure_as_c502():
    client = RapidOcrClient()

    class _BoomEngine:
        def __call__(self, *_args, **_kwargs):
            raise RuntimeError("boom")

    client._engine = _BoomEngine()

    with pytest.raises(BusinessException) as exc_info:
        await client.recognize(_png_bytes(1, 1))

    assert exc_info.value.error_code.code == "C502"


@pytest.mark.asyncio
async def test_rapidocr_recognize_returns_normalized_boxes():
    client = RapidOcrClient()

    boxes_arr = np.array(
        [
            [[10.0, 20.0], [110.0, 20.0], [110.0, 50.0], [10.0, 50.0]],
            [[10.0, 60.0], [200.0, 60.0], [200.0, 90.0], [10.0, 90.0]],
        ],
        dtype=float,
    )
    stub_result = SimpleNamespace(
        boxes=boxes_arr,
        txts=("홍길동", "주식회사 핀트"),
        scores=(0.99, 0.95),
    )

    class _StubEngine:
        def __call__(self, _arr, *_args, **_kwargs):
            return stub_result

    client._engine = _StubEngine()
    boxes = await client.recognize(_png_bytes())

    assert len(boxes) == 2
    assert boxes[0].text == "홍길동"
    assert boxes[0].font_height == pytest.approx(30.0)
    assert isinstance(boxes[1], OcrBox)
    assert boxes[1].text == "주식회사 핀트"


def test_run_inference_handles_empty_txts():
    stub_result = SimpleNamespace(boxes=None, txts=None, scores=None)

    class _EmptyEngine:
        def __call__(self, _arr, *_args, **_kwargs):
            return stub_result

    boxes = _run_inference(_EmptyEngine(), _png_bytes(1, 1))
    assert boxes == []


def test_run_inference_handles_zero_length_result():
    stub_result = SimpleNamespace(boxes=np.array([]), txts=(), scores=())

    class _Empty2:
        def __call__(self, _arr, *_args, **_kwargs):
            return stub_result

    boxes = _run_inference(_Empty2(), _png_bytes(1, 1))
    assert boxes == []
