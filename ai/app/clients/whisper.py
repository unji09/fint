from typing import Protocol, runtime_checkable

from openai import AsyncOpenAI

from app.core.errors import BusinessException, CommonErrorCode

DEFAULT_MODEL = "whisper-1"


@runtime_checkable
class WhisperClient(Protocol):
    async def transcribe(self, audio_bytes: bytes, *, language: str = "ko") -> str: ...


class OpenAIWhisperClient:
    def __init__(self, api_key: str) -> None:
        if not api_key:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "OPENAI_API_KEY not configured")
        self._client = AsyncOpenAI(api_key=api_key)

    async def transcribe(self, audio_bytes: bytes, *, language: str = "ko") -> str:
        import io

        audio_file = io.BytesIO(audio_bytes)
        audio_file.name = "audio.webm"
        resp = await self._client.audio.transcriptions.create(
            model=DEFAULT_MODEL,
            file=audio_file,
            language=language,
        )
        return resp.text
