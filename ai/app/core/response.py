from typing import Any, Generic, TypeVar

from pydantic import BaseModel, model_serializer

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    status: int
    code: str | None = None
    message: str
    data: T | None = None

    @model_serializer
    def _serialize(self) -> dict[str, Any]:
        result: dict[str, Any] = {"status": self.status}
        if self.code is not None:
            result["code"] = self.code
        result["message"] = self.message
        result["data"] = self.data
        return result

    @classmethod
    def ok(cls, data: T) -> "ApiResponse[T]":
        return cls(status=200, message="success", data=data)

    @classmethod
    def ok_empty(cls) -> "ApiResponse[None]":
        return cls(status=200, message="success")

    @classmethod
    def created(cls, data: T) -> "ApiResponse[T]":
        return cls(status=201, message="success", data=data)

    @classmethod
    def fail(cls, status: int, code: str, message: str) -> "ApiResponse[None]":
        return cls(status=status, code=code, message=message)
