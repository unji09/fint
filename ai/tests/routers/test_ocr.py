from pathlib import Path

import pytest
from httpx import ASGITransport, AsyncClient

from app.clients import (
    get_ocr_client,
    get_openai_client,
    get_s3_client,
)
from app.clients.ocr import OcrBox, RapidOcrClient
from app.core.errors import BusinessException, CommonErrorCode
from app.core.redis import get_redis
from app.core.security import get_tenant_id
from app.main import create_app
from app.schemas.ocr import BusinessCardClassification

_RESOURCES_DIR = Path(__file__).parent.parent / "resources"
_BUSINESS_CARD_IMAGE = _RESOURCES_DIR / "business_card.jpg"


class FakeS3:
    async def get_object(self, key: str) -> bytes:
        return b"fake-business-card-image"


class FakeS3Failing:
    async def get_object(self, key: str) -> bytes:
        raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "S3 down")


class FakeOcr:
    def __init__(self, boxes: list[OcrBox]) -> None:
        self._boxes = boxes
        self.calls = 0

    async def recognize(self, image_bytes: bytes) -> list[OcrBox]:
        self.calls += 1
        return list(self._boxes)


class FakeOcrFailing:
    async def recognize(self, image_bytes: bytes) -> list[OcrBox]:
        raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "OCR engine down")


class FakeOpenAI:
    def __init__(self, response: BusinessCardClassification | None = None, *, fail: bool = False) -> None:
        self._response = response or BusinessCardClassification()
        self._fail = fail
        self.calls = 0

    async def chat_structured(
        self, messages: list[dict], response_model, *, model: str | None = None
    ) -> BusinessCardClassification:
        self.calls += 1
        if self._fail:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "OpenAI 5xx")
        return self._response


class FakeRedis:
    def __init__(self) -> None:
        self.store: dict[str, str] = {}

    async def get(self, key: str) -> str | None:
        return self.store.get(key)

    async def set(self, key: str, value: str, *, ex: int | None = None) -> None:
        self.store[key] = value


def _box(text: str, font_height: float = 20.0, *, confidence: float = 0.99) -> OcrBox:
    return OcrBox(
        text=text,
        box=((0.0, 0.0), (100.0, 0.0), (100.0, font_height), (0.0, font_height)),
        font_height=font_height,
        confidence=confidence,
    )


CLEAR_BOXES: list[OcrBox] = [
    _box("주식회사 핀트", 40.0),
    _box("홍길동", 28.0),
    _box("이사", 18.0),
    _box("010-1234-5678", 14.0),
    _box("hong@fint.kr", 14.0),
]

AMBIGUOUS_BOXES: list[OcrBox] = [
    _box("Kim and Park", 30.0),
    _box("John Smith", 26.0),
    _box("010-9876-5432", 14.0),
    _box("john@kp.com", 14.0),
]


def _build_app(
    *,
    s3=FakeS3,
    ocr_instance: FakeOcr | FakeOcrFailing | None = None,
    openai_instance: FakeOpenAI | None = None,
    redis_instance: FakeRedis | None = None,
    with_auth: bool = True,
):
    app = create_app()
    if with_auth:
        app.dependency_overrides[get_tenant_id] = lambda: 1
    app.dependency_overrides[get_s3_client] = s3 if isinstance(s3, type) else lambda: s3

    ocr_obj = ocr_instance or FakeOcr(CLEAR_BOXES)
    app.dependency_overrides[get_ocr_client] = lambda: ocr_obj

    openai_obj = openai_instance or FakeOpenAI()
    app.dependency_overrides[get_openai_client] = lambda: openai_obj

    redis_obj = redis_instance or FakeRedis()

    async def _fake_get_redis():
        yield redis_obj

    app.dependency_overrides[get_redis] = _fake_get_redis
    return app, ocr_obj, openai_obj, redis_obj


@pytest.mark.asyncio
async def test_recognize_business_card_success_without_llm():
    app, ocr, openai, _ = _build_app(ocr_instance=FakeOcr(CLEAR_BOXES))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/123/card.jpg"},
        )

    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == 200
    assert "code" not in body
    data = body["data"]
    assert data["company"] == "주식회사 핀트"
    assert data["name"] == "홍길동"
    assert data["title"] == "이사"
    assert data["phone"] == "010-1234-5678"
    assert data["email"] == "hong@fint.kr"
    assert openai.calls == 0
    assert ocr.calls == 1


@pytest.mark.asyncio
async def test_korean_title_with_prefix_returns_full_phrase():
    boxes = [
        _box("(주)데모", 36.0),
        _box("이민정", 28.0),
        _box("수석 아키텍트", 18.0),
        _box("010-4567-8901", 14.0),
        _box("mj.lee@samsung.com", 14.0),
    ]
    app, *_ = _build_app(ocr_instance=FakeOcr(boxes))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/k/t.jpg"},
        )

    assert resp.status_code == 200
    assert resp.json()["data"]["title"] == "수석 아키텍트"


@pytest.mark.asyncio
async def test_phone_prefers_010_over_landline():
    boxes = [
        _box("주식회사 핀트", 36.0),
        _box("홍길동", 28.0),
        _box("이사", 18.0),
        _box("02-555-1234", 14.0),
        _box("010-1111-2222", 14.0),
        _box("hong@fint.kr", 14.0),
    ]
    app, *_ = _build_app(ocr_instance=FakeOcr(boxes))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/p/q.jpg"},
        )

    assert resp.status_code == 200
    assert resp.json()["data"]["phone"] == "010-1111-2222"


@pytest.mark.asyncio
async def test_phone_falls_back_to_02_when_no_010():
    boxes = [
        _box("주식회사 핀트", 36.0),
        _box("홍길동", 28.0),
        _box("이사", 18.0),
        _box("031-100-2000", 14.0),
        _box("02-555-1234", 14.0),
        _box("hong@fint.kr", 14.0),
    ]
    app, *_ = _build_app(ocr_instance=FakeOcr(boxes))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/p/r.jpg"},
        )

    assert resp.status_code == 200
    assert resp.json()["data"]["phone"] == "02-555-1234"


@pytest.mark.asyncio
async def test_high_confidence_english_company_without_suffix_skips_llm():
    boxes = [
        _box("DemoCorp", 36.0),
        _box("홍길동", 28.0),
        _box("이사", 18.0),
        _box("010-1234-5678", 14.0),
        _box("hong@democorp.com", 14.0),
    ]
    app, _, openai, _ = _build_app(ocr_instance=FakeOcr(boxes))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/c/d.jpg"},
        )

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["company"] == "DemoCorp"
    assert data["name"] == "홍길동"
    assert openai.calls == 0


@pytest.mark.asyncio
async def test_suspect_korean_company_triggers_llm():
    boxes = [
        _box("정위연위위험지", 36.0, confidence=0.62),
        _box("홍길동", 28.0),
        _box("이사", 18.0),
        _box("hong@realcorp.com", 14.0),
    ]
    llm_response = BusinessCardClassification(name="홍길동", company="RealCorp", title="이사")
    app, _, openai, _ = _build_app(
        ocr_instance=FakeOcr(boxes),
        openai_instance=FakeOpenAI(llm_response),
    )
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/s/k.jpg"},
        )

    assert resp.status_code == 200
    assert openai.calls == 1
    data = resp.json()["data"]
    assert data["company"] == "RealCorp"


@pytest.mark.asyncio
async def test_suspect_korean_excluded_from_company_picking():
    boxes = [
        _box("정위연위위험지", 40.0, confidence=0.55),
        _box("DemoCorp", 30.0),
        _box("홍길동", 24.0),
        _box("hong@democorp.com", 14.0),
    ]
    app, _, openai, _ = _build_app(ocr_instance=FakeOcr(boxes))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/x/y.jpg"},
        )

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["company"] == "DemoCorp"


@pytest.mark.asyncio
async def test_recognize_business_card_falls_back_to_llm_when_ambiguous():
    llm_response = BusinessCardClassification(name="John Smith", company="Kim and Park", title=None)
    app, _, openai, _ = _build_app(
        ocr_instance=FakeOcr(AMBIGUOUS_BOXES),
        openai_instance=FakeOpenAI(llm_response),
    )
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/456/card.jpg"},
        )

    assert resp.status_code == 200
    body = resp.json()
    assert openai.calls == 1
    data = body["data"]
    assert data["company"] == "Kim and Park"
    assert data["name"] == "John Smith"


@pytest.mark.asyncio
async def test_recognize_business_card_uses_cache_on_second_call():
    app, ocr, openai, redis = _build_app(ocr_instance=FakeOcr(CLEAR_BOXES))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        await ac.post("/api/v1/ocr/business-card", json={"s3_key": "business-cards/123/k1.jpg"})
        resp = await ac.post("/api/v1/ocr/business-card", json={"s3_key": "business-cards/123/k1.jpg"})

    assert resp.status_code == 200
    assert ocr.calls == 2
    assert openai.calls == 0
    assert len(redis.store) == 1


@pytest.mark.asyncio
async def test_recognize_missing_s3_key_returns_400():
    app, *_ = _build_app()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post("/api/v1/ocr/business-card", json={})

    assert resp.status_code == 400
    assert resp.json()["code"] == "C001"


@pytest.mark.asyncio
async def test_recognize_rejects_invalid_s3_key_prefix():
    app, *_ = _build_app()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "meetings/123/audio.webm"},
        )

    assert resp.status_code == 400
    assert resp.json()["code"] == "C001"


@pytest.mark.asyncio
async def test_recognize_isolates_cache_by_tenant():
    fake_redis = FakeRedis()
    fake_ocr = FakeOcr(CLEAR_BOXES)

    def _build_for(tenant: int):
        app = create_app()
        app.dependency_overrides[get_tenant_id] = lambda: tenant
        app.dependency_overrides[get_s3_client] = FakeS3
        app.dependency_overrides[get_ocr_client] = lambda: fake_ocr
        app.dependency_overrides[get_openai_client] = lambda: FakeOpenAI()

        async def _redis_dep():
            yield fake_redis

        app.dependency_overrides[get_redis] = _redis_dep
        return app

    transport_a = ASGITransport(app=_build_for(tenant=1))
    transport_b = ASGITransport(app=_build_for(tenant=2))

    async with AsyncClient(transport=transport_a, base_url="http://test") as ac:
        await ac.post("/api/v1/ocr/business-card", json={"s3_key": "business-cards/k.jpg"})
    async with AsyncClient(transport=transport_b, base_url="http://test") as ac:
        await ac.post("/api/v1/ocr/business-card", json={"s3_key": "business-cards/k.jpg"})

    assert len(fake_redis.store) == 2
    keys = list(fake_redis.store.keys())
    assert any(":1:" in k for k in keys)
    assert any(":2:" in k for k in keys)
    assert fake_ocr.calls == 2


@pytest.mark.asyncio
async def test_recognize_without_auth_returns_401():
    app, *_ = _build_app(with_auth=False)
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/123/card.jpg"},
        )

    assert resp.status_code == 401
    assert resp.json()["code"] == "C101"


@pytest.mark.asyncio
async def test_recognize_s3_failure_returns_502():
    app, *_ = _build_app(s3=FakeS3Failing)
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/missing.jpg"},
        )

    assert resp.status_code == 502
    assert resp.json()["code"] == "C502"


@pytest.mark.asyncio
async def test_recognize_ocr_failure_returns_502():
    app, *_ = _build_app(ocr_instance=FakeOcrFailing())
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/123/card.jpg"},
        )

    assert resp.status_code == 502
    assert resp.json()["code"] == "C502"


@pytest.mark.asyncio
async def test_recognize_llm_failure_falls_back_to_regex_only():
    app, _, openai, _ = _build_app(
        ocr_instance=FakeOcr(AMBIGUOUS_BOXES),
        openai_instance=FakeOpenAI(fail=True),
    )
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/789/card.jpg"},
        )

    assert resp.status_code == 200
    assert openai.calls == 1
    data = resp.json()["data"]
    assert data["phone"] == "010-9876-5432"
    assert data["email"] == "john@kp.com"


class _LocalFileS3:
    def __init__(self, path: Path) -> None:
        self._path = path

    async def get_object(self, key: str) -> bytes:
        return self._path.read_bytes()


@pytest.mark.skipif(
    not _BUSINESS_CARD_IMAGE.exists(),
    reason=(f"명함 이미지 없음 — {_BUSINESS_CARD_IMAGE.name} 을 tests/resources/ 에 두면 자동 실행. "),
)
@pytest.mark.asyncio
async def test_recognize_real_business_card_end_to_end(capsys):
    import time

    from app.core.config import get_settings

    app = create_app()
    app.dependency_overrides[get_tenant_id] = lambda: 1
    app.dependency_overrides[get_s3_client] = lambda: _LocalFileS3(_BUSINESS_CARD_IMAGE)
    real_ocr = RapidOcrClient()
    app.dependency_overrides[get_ocr_client] = lambda: real_ocr
    if not get_settings().OPENAI_API_KEY:
        app.dependency_overrides[get_openai_client] = lambda: FakeOpenAI(BusinessCardClassification())
    fake_redis = FakeRedis()

    async def _redis_dep():
        yield fake_redis

    app.dependency_overrides[get_redis] = _redis_dep

    transport = ASGITransport(app=app)
    started = time.perf_counter()
    async with AsyncClient(transport=transport, base_url="http://test", timeout=120.0) as ac:
        resp = await ac.post(
            "/api/v1/ocr/business-card",
            json={"s3_key": "business-cards/poc/sample.jpg"},
        )
    elapsed = time.perf_counter() - started

    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == 200
    data = body["data"]

    boxes = await real_ocr.recognize(_BUSINESS_CARD_IMAGE.read_bytes())
    raw_text = "\n".join(b.text for b in boxes)
    assert raw_text, "RapidOCR 가 텍스트를 하나도 인식하지 못함"

    with capsys.disabled():
        print(f"\n[OCR end-to-end PoC] {elapsed:.2f}s, raw {len(raw_text)} chars")
        print(f"  name:    {data['name']}")
        print(f"  company: {data['company']}")
        print(f"  title:   {data['title']}")
        print(f"  phone:   {data['phone']}")
        print(f"  email:   {data['email']}")
        print(f"  --- raw text ---\n{raw_text}")
        print("  --- boxes (font_height desc) ---")
        for b in sorted(boxes, key=lambda x: x.font_height, reverse=True):
            print(f"    [{b.font_height:6.1f}] {b.text}")
