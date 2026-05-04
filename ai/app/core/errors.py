import logging
from enum import Enum
from typing import Protocol, runtime_checkable

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from app.core.response import ApiResponse

logger = logging.getLogger(__name__)


@runtime_checkable
class ErrorCode(Protocol):
    @property
    def status(self) -> int: ...

    @property
    def code(self) -> str: ...

    @property
    def message(self) -> str: ...


class CommonErrorCode(Enum):
    INVALID_INPUT = (400, "C001", "Invalid input format")
    ILLEGAL_ARGUMENT = (400, "C002", "Illegal argument")
    UNAUTHORIZED = (401, "C101", "Unauthorized")
    FORBIDDEN = (403, "C201", "Forbidden")
    NOT_FOUND = (404, "C301", "Resource not found")
    CONFLICT = (409, "C401", "Resource conflict")
    INTERNAL_SERVER_ERROR = (500, "C500", "Internal server error")
    EXTERNAL_API_FAILED = (502, "C502", "External API call failed")

    def __init__(self, status: int, code: str, message: str) -> None:
        self._status = status
        self._code = code
        self._message = message

    @property
    def status(self) -> int:
        return self._status

    @property
    def code(self) -> str:
        return self._code

    @property
    def message(self) -> str:
        return self._message


class BusinessException(Exception):
    def __init__(self, error_code: ErrorCode, message: str | None = None) -> None:
        self.error_code = error_code
        self.detail = message or error_code.message
        super().__init__(self.detail)


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(BusinessException)
    async def _handle_business(request: Request, exc: BusinessException) -> JSONResponse:
        logger.warning("[%s] %s", exc.error_code.code, exc.detail)
        body = ApiResponse.fail(exc.error_code.status, exc.error_code.code, exc.detail)
        return JSONResponse(status_code=exc.error_code.status, content=body.model_dump())

    @app.exception_handler(RequestValidationError)
    async def _handle_validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        field_errors = {
            ".".join(str(loc) for loc in e["loc"]): e["msg"]
            for e in exc.errors()
        }
        logger.warning("[ValidationError] %s", field_errors)
        ec = CommonErrorCode.INVALID_INPUT
        body = ApiResponse(status=ec.status, code=ec.code, message=ec.message, data=field_errors)
        return JSONResponse(status_code=ec.status, content=body.model_dump())

    @app.exception_handler(HTTPException)
    async def _handle_http(request: Request, exc: HTTPException) -> JSONResponse:
        matched = next((e for e in CommonErrorCode if e.status == exc.status_code), None)
        if matched:
            body = ApiResponse.fail(matched.status, matched.code, str(exc.detail))
        else:
            body = ApiResponse.fail(exc.status_code, None, str(exc.detail))  # type: ignore[arg-type]
        return JSONResponse(status_code=exc.status_code, content=body.model_dump())

    @app.exception_handler(Exception)
    async def _handle_generic(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("[InternalError] %s", exc)
        ec = CommonErrorCode.INTERNAL_SERVER_ERROR
        body = ApiResponse.fail(ec.status, ec.code, ec.message)
        return JSONResponse(status_code=ec.status, content=body.model_dump())
