from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.clients import warmup_embedder_client
from app.clients.gpu_stt import GpuSttClient
from app.core.config import get_settings
from app.core.db import close_db, init_db
from app.core.errors import register_exception_handlers
from app.core.redis import close_redis, init_redis
<<<<<<< ai/app/main.py
from app.routers import dashboard, health, mood, news, ocr, strategy, stt, stt_stream
from app.core.speaker_session import SpeakerSessionManager


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    import logging

    settings = get_settings()
    logging.basicConfig(level=settings.LOG_LEVEL, force=True)
    init_db(settings.database_url)
    init_redis(settings.redis_url)

    from pathlib import Path

    model_path = Path(settings.EMBEDDING_MODEL_PATH)
    if model_path.exists() and (model_path / "model.onnx").exists():
        logging.getLogger(__name__).info("Loading embedding model from %s", model_path)
        warmup_embedder_client(str(model_path))
    else:
        logging.getLogger(__name__).info("Embedding model not found at %s, semantic search disabled", model_path)

    _app.state.speaker_session_manager = SpeakerSessionManager()
    if settings.GPU_SERVER_URL:
        _app.state.gpu_stt_client = GpuSttClient(base_url=settings.GPU_SERVER_URL)
        logging.getLogger(__name__).info("GPU STT client initialized: %s", settings.GPU_SERVER_URL)
    else:
        _app.state.gpu_stt_client = None
        logging.getLogger(__name__).warning("GPU_SERVER_URL not set — STT diarize/stream disabled")

    yield

    if _app.state.gpu_stt_client is not None:
        await _app.state.gpu_stt_client.close()
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
    app.include_router(stt_stream.router)
    app.include_router(dashboard.router)
    app.include_router(ocr.router)
    app.include_router(strategy.router)
    app.include_router(news.router)
    app.include_router(mood.router)

    return app
