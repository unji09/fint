from fastapi import Request
from jose import JWTError, jwt

from app.core.config import get_settings
from app.core.errors import BusinessException, CommonErrorCode


def _validate_tenant_id(value: int) -> int:
    if value <= 0:
        raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid tenant_id")
    return value


async def get_tenant_id(request: Request) -> int:
    auth_header = request.headers.get("Authorization")
    if auth_header and auth_header.startswith("Bearer "):
        secret = get_settings().JWT_SECRET
        if not secret:
            raise BusinessException(CommonErrorCode.UNAUTHORIZED, "JWT secret not configured")
        token = auth_header.removeprefix("Bearer ")
        try:
            payload = jwt.decode(token, secret, algorithms=["HS256"])
            tenant_id = payload.get("tenant_id")
            if tenant_id is None:
                raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Missing tenant_id in token")
            return _validate_tenant_id(int(tenant_id))
        except (JWTError, ValueError, KeyError):
            raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid token")

    tenant_header = request.headers.get("X-Tenant-Id")
    if tenant_header:
        try:
            return _validate_tenant_id(int(tenant_header))
        except ValueError:
            raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid X-Tenant-Id")

    raise BusinessException(CommonErrorCode.UNAUTHORIZED)


def decode_tenant_id(token: str) -> int:
    secret = get_settings().JWT_SECRET
    if not secret:
        raise BusinessException(CommonErrorCode.UNAUTHORIZED, "JWT secret not configured")
    try:
        payload = jwt.decode(token, secret, algorithms=["HS256"])
        tenant_id = payload.get("tenant_id")
        if tenant_id is None:
            raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Missing tenant_id in token")
        return _validate_tenant_id(int(tenant_id))
    except (JWTError, ValueError, KeyError):
        raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid token")
