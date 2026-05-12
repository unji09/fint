import httpx

from app.core.errors import BusinessException, CommonErrorCode


class SpringClient:
    def __init__(self, base_url: str, internal_secret: str) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=30.0,
        )
        self._secret = internal_secret

    async def send_mood_callback(
        self,
        activity_id: int,
        account_id: int,
        mood_score: int,
        reason: str,
        key_signals: list[str],
    ) -> None:
        try:
            resp = await self._client.post(
                f"/api/v1/internal/activities/{activity_id}/ai/mood/callback",
                json={
                    "accountId": account_id,
                    "moodScore": mood_score,
                    "reason": reason,
                    "keySignals": key_signals,
                },
                headers={"X-Internal-Secret": self._secret},
            )
            resp.raise_for_status()
        except httpx.HTTPStatusError as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                f"Spring 콜백 실패: {e.response.status_code}",
            ) from e
        except (httpx.TimeoutException, httpx.ConnectError) as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED,
                "Spring 서버 연결 실패",
            ) from e

    async def close(self) -> None:
        await self._client.aclose()