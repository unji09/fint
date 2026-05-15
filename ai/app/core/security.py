import base64

from fastapi import Request
from jose import JWTError, jwt

from app.core.config import get_settings
from app.core.errors import BusinessException, CommonErrorCode


_ALLOWED_ALGORITHMS = ["HS256", "HS384", "HS512"]


def _decode_secret(secret: str) -> bytes:
    padding = 4 - len(secret) % 4
    if padding != 4:
        secret = secret + "=" * padding
    return base64.urlsafe_b64decode(secret)


def _validate_tenant_id(value: int) -> int:
    if value <= 0:
        raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid tenant_id")
    return value


def _extract_tenant_id_from_payload(payload: dict) -> int:
    raw = payload.get("tenant_id") or payload.get("tenantId")
    if raw is None:
        raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Missing tenant_id in token")
    return _validate_tenant_id(int(raw))


async def get_tenant_id(request: Request) -> int:
    auth_header = request.headers.get("Authorization")
    if auth_header and auth_header.startswith("Bearer "):
        raw_secret = get_settings().JWT_SECRET
        if not raw_secret:
            raise BusinessException(CommonErrorCode.UNAUTHORIZED, "JWT secret not configured")
        key = _decode_secret(raw_secret)
        token = auth_header.removeprefix("Bearer ")
        try:
            payload = jwt.decode(token, key, algorithms=_ALLOWED_ALGORITHMS)
            return _extract_tenant_id_from_payload(payload)
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
    raw_secret = get_settings().JWT_SECRET
    if not raw_secret:
        raise BusinessException(CommonErrorCode.UNAUTHORIZED, "JWT secret not configured")
    key = _decode_secret(raw_secret)
    try:
        payload = jwt.decode(token, key, algorithms=_ALLOWED_ALGORITHMS)
        return _extract_tenant_id_from_payload(payload)
    except (JWTError, ValueError, KeyError):
        raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid token")
