from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.clients import warmup_embedder_client, warmup_ocr_client
from app.core.config import get_settings
from app.core.db import close_db, init_db
from app.core.errors import register_exception_handlers
from app.core.redis import close_redis, init_redis
from app.routers import dashboard, health, ocr, stt


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    import logging

    settings = get_settings()
    logging.basicConfig(level=settings.LOG_LEVEL, force=True)
    init_db(settings.database_url)
    init_redis(settings.redis_url)
    try:
        await warmup_ocr_client().warmup()
    except Exception:
        logging.getLogger(__name__).warning("OCR warmup failed — OCR endpoint disabled")

    from pathlib import Path

    model_path = Path(settings.EMBEDDING_MODEL_PATH)
    if model_path.exists() and (model_path / "model.onnx").exists():
        logging.getLogger(__name__).info("Loading embedding model from %s", model_path)
        warmup_embedder_client(str(model_path))
    else:
        logging.getLogger(__name__).info("Embedding model not found at %s, semantic search disabled", model_path)

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
    app.include_router(dashboard.router)
    app.include_router(ocr.router)

    return app
