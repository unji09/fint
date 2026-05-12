from __future__ import annotations

import httpx

from app.core.errors import (
    BusinessException,
    CommonErrorCode,
)
from app.schemas.ocr import (
    BusinessCardOcrResponse,
)


class GpuServerClient:
    def __init__(
        self,
        base_url: str,
        timeout: float = 60.0,
    ) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout,
        )

    async def extract_business_card(
        self,
        image_bytes: bytes,
    ) -> BusinessCardOcrResponse:
        try:
            response = await self._client.post(
                "/business-card",
                content=image_bytes,
                headers={
                    "Content-Type": "application/octet-stream",
                },
            )

            response.raise_for_status()

            return BusinessCardOcrResponse.model_validate(
                response.json()
            )

        except httpx.HTTPStatusError as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                f"GPU 서버 응답 오류: {e.response.status_code}",
            ) from e

        except (
            httpx.TimeoutException,
            httpx.ConnectError,
        ) as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                "GPU 서버 연결 실패",
            ) from e

        except Exception as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                f"GPU 서버 호출 실패: {e}",
            ) from e

    async def close(self) -> None:
        await self._client.aclose()