from typing import Any, Protocol, runtime_checkable

from pydantic import BaseModel


@runtime_checkable
class LLMClient(Protocol):
    async def chat(self, messages: list[dict], *, model: str | None = None) -> str: ...

    async def chat_structured(
        self, messages: list[dict], response_model: type[BaseModel], *, model: str | None = None
    ) -> BaseModel: ...

    async def chat_with_tools(
        self, messages: list[dict], tools: list[dict], *, model: str | None = None
    ) -> Any: ...
