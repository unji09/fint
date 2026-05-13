from __future__ import annotations

import httpx
from pydantic import ValidationError

from app.core.errors import BusinessException, CommonErrorCode
from app.schemas.stt import SttDiarizedResponse, SttSegment


class GpuSttClient:
    def __init__(
        self,
        base_url: str,
        timeout: float = 120.0,
    ) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout,
        )

    async def transcribe_chunk(
        self,
        audio_bytes: bytes,
        language: str = "ko",
    ) -> dict:
        try:
            response = await self._client.post(
                "/stt/chunk",
                content=audio_bytes,
                headers={
                    "Content-Type": "application/octet-stream",
                    "X-Language": language,
                },
            )
            response.raise_for_status()
            return response.json()

        except httpx.HTTPStatusError as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                f"GPU STT 청크 오류: {e.response.status_code}",
            ) from e

        except (httpx.TimeoutException, httpx.ConnectError) as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                "GPU STT 서버 연결 실패",
            ) from e

    async def transcribe_batch(
        self,
        audio_bytes: bytes,
        language: str = "ko",
        num_speakers: int | None = None,
    ) -> SttDiarizedResponse:
        headers: dict[str, str] = {
            "Content-Type": "application/octet-stream",
            "X-Language": language,
        }
        if num_speakers is not None:
            headers["X-Num-Speakers"] = str(num_speakers)

        try:
            response = await self._client.post(
                "/stt/batch",
                content=audio_bytes,
                headers=headers,
            )
            response.raise_for_status()
            try:
                data = response.json()
                return SttDiarizedResponse(
                    segments=[SttSegment(**seg) for seg in data["segments"]]
                )
            except (KeyError, ValueError, ValidationError) as e:
                raise BusinessException(
                    CommonErrorCode.EXTERNAL_API_FAILED,
                    f"GPU STT 응답 파싱 실패: {e}",
                ) from e

        except httpx.HTTPStatusError as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                f"GPU STT 배치 오류: {e.response.status_code}",
            ) from e

        except (httpx.TimeoutException, httpx.ConnectError) as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                "GPU STT 서버 연결 실패",
            ) from e

    async def close(self) -> None:
        await self._client.aclose()
