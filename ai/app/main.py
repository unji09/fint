from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI

from app.core.errors import register_exception_handlers
from app.routers import health


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title="F!NT AI Service",
        version="0.1.0",
        lifespan=lifespan,
    )

    register_exception_handlers(app)
    app.include_router(health.router)

    return app
