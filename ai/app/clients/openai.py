import instructor
from openai import AsyncOpenAI
from pydantic import BaseModel

from app.core.errors import BusinessException, CommonErrorCode

DEFAULT_MODEL = "gpt-4o"


class OpenAIClient:
    def __init__(self, api_key: str) -> None:
        if not api_key:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "OPENAI_API_KEY not configured")
        self._raw = AsyncOpenAI(api_key=api_key)
        self._instructor = instructor.from_openai(self._raw)

    async def chat(self, messages: list[dict], *, model: str | None = None) -> str:
        resp = await self._raw.chat.completions.create(
            model=model or DEFAULT_MODEL,
            messages=messages,
        )
        return resp.choices[0].message.content or ""

    async def chat_structured(
        self, messages: list[dict], response_model: type[BaseModel], *, model: str | None = None
    ) -> BaseModel:
        return await self._instructor.chat.completions.create(
            model=model or DEFAULT_MODEL,
            messages=messages,
            response_model=response_model,
        )
