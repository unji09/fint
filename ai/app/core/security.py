from fastapi import Request

from jose import JWTError, jwt

from app.core.config import get_settings
from app.core.errors import BusinessException, CommonErrorCode


async def get_tenant_id(request: Request) -> int:
    auth_header = request.headers.get("Authorization")
    if auth_header and auth_header.startswith("Bearer "):
        token = auth_header.removeprefix("Bearer ")
        try:
            payload = jwt.decode(token, get_settings().JWT_SECRET, algorithms=["HS256"])
            tenant_id = payload.get("tenant_id")
            if tenant_id is not None:
                return int(tenant_id)
        except (JWTError, ValueError, KeyError):
            raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid token")

    tenant_header = request.headers.get("X-Tenant-Id")
    if tenant_header:
        try:
            return int(tenant_header)
        except ValueError:
            raise BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid X-Tenant-Id")

    raise BusinessException(CommonErrorCode.UNAUTHORIZED)
