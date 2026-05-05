import instructor
from anthropic import AsyncAnthropic
from pydantic import BaseModel

from app.core.errors import BusinessException, CommonErrorCode

DEFAULT_MODEL = "claude-sonnet-4-20250514"
DEFAULT_MAX_TOKENS = 4096


class AnthropicClient:
    def __init__(self, api_key: str) -> None:
        if not api_key:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "ANTHROPIC_API_KEY not configured")
        self._raw = AsyncAnthropic(api_key=api_key)
        self._instructor = instructor.from_anthropic(self._raw)

    async def chat(self, messages: list[dict], *, model: str | None = None) -> str:
        resp = await self._raw.messages.create(
            model=model or DEFAULT_MODEL,
            max_tokens=DEFAULT_MAX_TOKENS,
            messages=messages,
        )
        return resp.content[0].text

    async def chat_structured(
        self, messages: list[dict], response_model: type[BaseModel], *, model: str | None = None
    ) -> BaseModel:
        return await self._instructor.messages.create(
            model=model or DEFAULT_MODEL,
            max_tokens=DEFAULT_MAX_TOKENS,
            messages=messages,
            response_model=response_model,
        )
