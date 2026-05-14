import instructor
import openai
from openai import AsyncOpenAI
from pydantic import BaseModel

from app.core.errors import BusinessException, CommonErrorCode

DEFAULT_MODEL = "gpt-4o"


class OpenAIClient:
    def __init__(self, api_key: str, base_url: str | None = None) -> None:
        if not api_key:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "OPENAI_API_KEY not configured")
        self._raw = AsyncOpenAI(api_key=api_key, base_url=base_url) if base_url else AsyncOpenAI(api_key=api_key)
        self._instructor = instructor.from_openai(self._raw, mode=instructor.Mode.MD_JSON)

    async def chat(self, messages: list[dict], *, model: str | None = None) -> str:
        try:
            resp = await self._raw.chat.completions.create(
                model=model or DEFAULT_MODEL,
                messages=messages,
            )
        except openai.OpenAIError as e:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, f"OpenAI chat failed: {e}") from e
        return resp.choices[0].message.content or ""

    async def chat_structured(
        self, messages: list[dict], response_model: type[BaseModel], *, model: str | None = None
    ) -> BaseModel:
        try:
            return await self._instructor.chat.completions.create(
                model=model or DEFAULT_MODEL,
                messages=messages,
                response_model=response_model,
            )
        except openai.OpenAIError as e:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, f"OpenAI chat_structured failed: {e}") from e

    async def chat_with_tools(self, messages: list[dict], tools: list[dict], *, model: str | None = None):
        try:
            resp = await self._raw.chat.completions.create(
                model=model or DEFAULT_MODEL,
                messages=messages,
                tools=tools,
            )
            return resp.choices[0].message
        except openai.OpenAIError as e:
            raise BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, f"OpenAI chat_with_tools failed: {e}") from e
