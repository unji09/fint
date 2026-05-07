from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.clients import warmup_ocr_client
from app.core.config import get_settings
from app.core.db import close_db, init_db
from app.core.errors import register_exception_handlers
from app.core.redis import close_redis, init_redis
from app.routers import health, ocr, stt


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    import logging

    settings = get_settings()
    logging.basicConfig(level=settings.LOG_LEVEL, force=True)
    init_db(settings.database_url)
    init_redis(settings.redis_url)
    await warmup_ocr_client().warmup()
    yield
    await close_redis()
    await close_db()


def create_app() -> FastAPI:
    app = FastAPI(
        title="F!NT AI Service",
        version="0.1.0",
        lifespan=lifespan,
    )

    register_exception_handlers(app)
    app.include_router(health.router)
    app.include_router(stt.router)
    app.include_router(ocr.router)

    return app
