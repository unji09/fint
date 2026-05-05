from collections.abc import AsyncIterator

from redis.asyncio import Redis, from_url

_redis: Redis | None = None


def init_redis(redis_url: str) -> None:
    global _redis
    _redis = from_url(redis_url, decode_responses=True)


async def close_redis() -> None:
    global _redis
    if _redis:
        await _redis.aclose()
        _redis = None


async def get_redis() -> AsyncIterator[Redis]:
    assert _redis is not None, "Redis not initialized. Call init_redis() in lifespan."
    yield _redis
