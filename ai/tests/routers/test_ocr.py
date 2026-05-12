"""POST /api/v1/ocr/business-card 라우터 통합 테스트."""

from unittest.mock import AsyncMock, MagicMock

import pytest
from httpx import ASGITransport, AsyncClient

from app.clients import (
    get_gpu_client,
    get_s3_client,
)
from app.clients.gpu import GpuServerClient
from app.core.errors import (
    BusinessException,
    CommonErrorCode,
)
from app.core.redis import get_redis
from app.core.security import get_tenant_id
from tests.routers.conftest import (
    IMAGE_BYTES,
    SAMPLE_RESPONSE,
)

_URL = "/api/v1/ocr/business-card"
_VALID_KEY = "business-cards/1/uuid.jpg"


# ── 정상 케이스 ─────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_cache_miss_calls_gpu_and_stores_result(
    ac: AsyncClient,
    mock_gpu: MagicMock,
    mock_redis: MagicMock,
) -> None:
    """캐시 미스 → GPU 서버 호출 → 결과 캐시 저장 → 200 반환."""

    resp = await ac.post(
        _URL,
        json={"s3_key": _VALID_KEY},
    )

    assert resp.status_code == 200

    body = resp.json()
    data = body["data"]

    assert data["name"] == SAMPLE_RESPONSE.name
    assert data["company"] == SAMPLE_RESPONSE.company
    assert data["title"] == SAMPLE_RESPONSE.title
    assert data["phone"] == SAMPLE_RESPONSE.phone
    assert data["email"] == SAMPLE_RESPONSE.email

    mock_gpu.extract_business_card.assert_called_once_with(
        IMAGE_BYTES,
    )

    mock_redis.set.assert_called_once()


@pytest.mark.asyncio
async def test_cache_hit_skips_gpu(
    test_app,
    mock_gpu: MagicMock,
) -> None:
    """캐시 히트 → GPU 서버 미호출."""

    cached_json = SAMPLE_RESPONSE.model_dump_json()

    mock_redis_hit = MagicMock()

    mock_redis_hit.get = AsyncMock(
        return_value=cached_json.encode(),
    )

    mock_redis_hit.set = AsyncMock()

    test_app.dependency_overrides[get_redis] = (
        lambda: mock_redis_hit
    )

    async with AsyncClient(
        transport=ASGITransport(app=test_app),
        base_url="http://test",
    ) as client:
        resp = await client.post(
            _URL,
            json={"s3_key": _VALID_KEY},
        )

    assert resp.status_code == 200

    body = resp.json()

    assert (
        body["data"]["name"]
        == SAMPLE_RESPONSE.name
    )

    mock_gpu.extract_business_card.assert_not_called()

    mock_redis_hit.set.assert_not_called()


# ── 오류 케이스 ─────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_invalid_s3_key_prefix_returns_400(
    ac: AsyncClient,
) -> None:
    """s3_key 프리픽스가 'business-cards/' 아니면 400."""

    resp = await ac.post(
        _URL,
        json={
            "s3_key": "uploads/something.jpg",
        },
    )

    assert resp.status_code == 400

    body = resp.json()

    assert body["code"] == "C001"

    assert "business-cards/" in body["message"]


@pytest.mark.asyncio
async def test_missing_s3_key_returns_400(
    ac: AsyncClient,
) -> None:
    """s3_key 누락 시 Pydantic 검증 오류 → 400."""

    resp = await ac.post(
        _URL,
        json={},
    )

    assert resp.status_code == 400

    body = resp.json()

    assert body["code"] == "C001"


@pytest.mark.asyncio
async def test_missing_tenant_returns_401(
    test_app,
) -> None:
    """인증 헤더 없으면 401."""

    test_app.dependency_overrides.pop(
        get_tenant_id,
        None,
    )

    async with AsyncClient(
        transport=ASGITransport(app=test_app),
        base_url="http://test",
    ) as client:
        resp = await client.post(
            _URL,
            json={"s3_key": _VALID_KEY},
        )

    assert resp.status_code == 401

    body = resp.json()

    assert body["code"] == "C101"


@pytest.mark.asyncio
async def test_gpu_error_returns_502(
    test_app,
) -> None:
    """GPU 서버 오류 → 502."""

    failing_gpu = MagicMock(
        spec=GpuServerClient,
    )

    failing_gpu.extract_business_card = AsyncMock(
        side_effect=BusinessException(
            CommonErrorCode.EXTERNAL_API_FAILED,
            "GPU 서버 호출 실패",
        ),
    )

    test_app.dependency_overrides[get_gpu_client] = (
        lambda: failing_gpu
    )

    async with AsyncClient(
        transport=ASGITransport(app=test_app),
        base_url="http://test",
    ) as client:
        resp = await client.post(
            _URL,
            json={"s3_key": _VALID_KEY},
        )

    assert resp.status_code == 502

    body = resp.json()

    assert body["code"] == "C502"


@pytest.mark.asyncio
async def test_s3_error_returns_502(
    test_app,
    mock_gpu: MagicMock,
) -> None:
    """S3 다운로드 실패 → 502."""

    from app.clients.s3 import S3Client

    failing_s3 = MagicMock(
        spec=S3Client,
    )

    failing_s3.get_object = AsyncMock(
        side_effect=BusinessException(
            CommonErrorCode.EXTERNAL_API_FAILED,
            "S3 down",
        ),
    )

    test_app.dependency_overrides[get_s3_client] = (
        lambda: failing_s3
    )

    async with AsyncClient(
        transport=ASGITransport(app=test_app),
        base_url="http://test",
    ) as client:
        resp = await client.post(
            _URL,
            json={"s3_key": _VALID_KEY},
        )

    assert resp.status_code == 502

    mock_gpu.extract_business_card.assert_not_called()


# ── 멀티테넌트 격리 ──────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_different_tenants_use_different_cache_keys(
    test_app,
) -> None:
    """tenant_id가 다르면 캐시 키도 달라야 한다."""

    stored_keys: list[str] = []

    mock_redis_spy = MagicMock()

    mock_redis_spy.get = AsyncMock(
        return_value=None,
    )

    async def _spy_set(
        key: str,
        value: str,
        **kwargs: object,
    ) -> None:
        stored_keys.append(key)

    mock_redis_spy.set = AsyncMock(
        side_effect=_spy_set,
    )

    test_app.dependency_overrides[get_redis] = (
        lambda: mock_redis_spy
    )

    for tenant_id in (1, 2):
        test_app.dependency_overrides[
            get_tenant_id
        ] = lambda tid=tenant_id: tid

        async with AsyncClient(
            transport=ASGITransport(app=test_app),
            base_url="http://test",
        ) as client:
            await client.post(
                _URL,
                json={"s3_key": _VALID_KEY},
            )

    assert len(stored_keys) == 2

    assert len(set(stored_keys)) == 2

    assert ":1:" in stored_keys[0]
    assert ":2:" in stored_keys[1]