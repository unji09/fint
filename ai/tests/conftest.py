from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import FastAPI
from httpx import (
    ASGITransport,
    AsyncClient,
)

from app.clients import (
    get_gpu_client,
    get_s3_client,
)
from app.clients.gpu import GpuServerClient
from app.clients.s3 import S3Client
from app.core.errors import (
    register_exception_handlers,
)
from app.core.redis import get_redis
from app.core.security import get_tenant_id
from app.routers.ocr import router
from app.schemas.ocr import (
    BusinessCardOcrResponse,
)

TENANT_ID = 1

IMAGE_BYTES = b"fake-image-bytes"

SAMPLE_RESPONSE = BusinessCardOcrResponse(
    name="박지훈",
    company="Nova Systems",
    title="AI Engineer",
    phone="010-2468-1357",
    email="jihoon.park@novasystems.ai",
)


@pytest.fixture
def mock_s3() -> MagicMock:
    s3 = MagicMock(spec=S3Client)

    s3.get_object = AsyncMock(
        return_value=IMAGE_BYTES
    )

    return s3


@pytest.fixture
def mock_gpu() -> MagicMock:
    gpu = MagicMock(
        spec=GpuServerClient,
    )

    gpu.extract_business_card = AsyncMock(
        return_value=SAMPLE_RESPONSE
    )

    return gpu


@pytest.fixture
def mock_redis() -> MagicMock:
    redis = MagicMock()

    # 기본: 캐시 미스
    redis.get = AsyncMock(
        return_value=None
    )

    redis.set = AsyncMock()

    return redis


@pytest.fixture
def test_app(
    mock_s3: MagicMock,
    mock_gpu: MagicMock,
    mock_redis: MagicMock,
) -> FastAPI:
    """lifespan 없이 라우터만 올린 테스트용 앱."""

    app = FastAPI()

    register_exception_handlers(app)

    app.include_router(router)

    app.dependency_overrides = {
        get_tenant_id: lambda: TENANT_ID,
        get_s3_client: lambda: mock_s3,
        get_gpu_client: lambda: mock_gpu,
        get_redis: lambda: mock_redis,
    }

    return app


@pytest.fixture
async def ac(
    test_app: FastAPI,
) -> AsyncClient:
    async with AsyncClient(
        transport=ASGITransport(app=test_app),
        base_url="http://test",
    ) as client:
        yield client