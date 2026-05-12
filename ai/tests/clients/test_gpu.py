"""GpuServerClient 테스트.

- 단위 테스트: httpx.AsyncClient를 목으로 교체
- E2E 테스트:  @pytest.mark.real_gpu — GPU_SERVER_URL 환경변수 필요
"""
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest
import pytest_asyncio

from app.clients.gpu import GpuServerClient
from app.core.config import get_settings
from app.core.errors import BusinessException
IMAGE_BYTES = b"fake-image-bytes"

settings = get_settings()


# ── 헬퍼 ─────────────────────────────────────────────────────────────────────

def _make_client_with_mock_http(json_data: dict) -> tuple[GpuServerClient, AsyncMock]:
    """GpuServerClient._client.post를 목으로 교체한 인스턴스를 반환."""
    client = GpuServerClient(base_url="http://gpu-server:8002")
    mock_post = AsyncMock()

    mock_response = MagicMock()
    mock_response.raise_for_status = MagicMock()
    mock_response.json.return_value = json_data
    mock_post.return_value = mock_response

    client._client = MagicMock()
    client._client.post = mock_post
    return client, mock_post


# ── 단위 테스트 ───────────────────────────────────────────────────────────────

async def test_extract_business_card_returns_full_fields() -> None:
    client, _ = _make_client_with_mock_http({
        "name": "박지훈",
        "company": "Nova Systems",
        "title": "AI Engineer",
        "phone": "010-2468-1357",
        "email": "jihoon.park@novasystems.ai",
    })

    result = await client.extract_business_card(IMAGE_BYTES)

    assert result.name == "박지훈"
    assert result.company == "Nova Systems"
    assert result.title == "AI Engineer"
    assert result.phone == "010-2468-1357"
    assert result.email == "jihoon.park@novasystems.ai"


async def test_extract_business_card_null_fields_allowed() -> None:
    """부서 없는 명함 — title null 허용."""
    client, _ = _make_client_with_mock_http({
        "name": "이주호",
        "company": "SalesMap",
        "title": None,
        "phone": "010-5923-4550",
        "email": "jooholee@salesmap.kr",
    })

    result = await client.extract_business_card(IMAGE_BYTES)

    assert result.name == "이주호"
    assert result.title is None


async def test_extract_business_card_sends_correct_request() -> None:
    """이미지 bytes를 /business-card 로 octet-stream 전송."""
    client, mock_post = _make_client_with_mock_http({
        "name": None, "company": None, "title": None,
        "phone": None, "email": None,
    })

    await client.extract_business_card(IMAGE_BYTES)

    mock_post.assert_called_once_with(
        "/business-card",
        content=IMAGE_BYTES,
        headers={"Content-Type": "application/octet-stream"},
    )


async def test_http_error_raises_502() -> None:
    """GPU 서버 5xx → BusinessException(502)."""
    client = GpuServerClient(base_url="http://gpu-server:8002")
    mock_post = AsyncMock(side_effect=httpx.HTTPStatusError(
        "500 Internal Server Error",
        request=MagicMock(),
        response=MagicMock(status_code=500),
    ))
    client._client = MagicMock()
    client._client.post = mock_post

    with pytest.raises(BusinessException) as exc_info:
        await client.extract_business_card(IMAGE_BYTES)

    assert exc_info.value.error_code.status == 502


async def test_connection_error_raises_502() -> None:
    """GPU 서버 접속 불가 → BusinessException(502)."""
    client = GpuServerClient(base_url="http://gpu-server:8002")
    client._client = MagicMock()
    client._client.post = AsyncMock(
        side_effect=httpx.ConnectError("Connection refused")
    )

    with pytest.raises(BusinessException) as exc_info:
        await client.extract_business_card(IMAGE_BYTES)

    assert exc_info.value.error_code.status == 502


async def test_timeout_raises_502() -> None:
    """GPU 서버 타임아웃 → BusinessException(502)."""
    client = GpuServerClient(base_url="http://gpu-server:8002")
    client._client = MagicMock()
    client._client.post = AsyncMock(
        side_effect=httpx.TimeoutException("timed out")
    )

    with pytest.raises(BusinessException) as exc_info:
        await client.extract_business_card(IMAGE_BYTES)

    assert exc_info.value.error_code.status == 502


# ── E2E 테스트 (실제 GPU 서버 필요) ──────────────────────────────────────────

@pytest_asyncio.fixture
async def gpu_client() -> GpuServerClient:  # type: ignore[misc]
    if not settings.GPU_SERVER_URL:
        pytest.skip("GPU_SERVER_URL 환경변수가 설정되지 않았습니다.")
    client = GpuServerClient(base_url=settings.GPU_SERVER_URL)
    yield client
    await client.close()


@pytest.mark.real_gpu
async def test_real_business_card_ocr(gpu_client: GpuServerClient) -> None:
    """실제 GPU OCR 서버 연동 테스트. tests/resources/card.jpg 필요."""
    image_path = Path(__file__).parent.parent / "resources" / "1.jpg"

    if not image_path.exists():
        pytest.skip(f"테스트 이미지가 없습니다: {image_path}")

    result = await gpu_client.extract_business_card(image_path.read_bytes())

    assert result is not None
    assert result.name is not None and isinstance(result.name, str)
    if result.email is not None:
        assert "@" in result.email

    print(f"\n=== OCR RESULT ===")
    print(f"name    : {result.name}")
    print(f"company : {result.company}")
    print(f"title   : {result.title}")
    print(f"phone   : {result.phone}")
    print(f"email   : {result.email}")
