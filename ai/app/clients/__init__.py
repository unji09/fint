from functools import lru_cache

from fastapi import Depends

from app.clients.anthropic import AnthropicClient
from app.clients.embedder import OnnxEmbedderClient
from app.clients.gpu import GpuServerClient
from app.clients.llm import LLMClient
from app.clients.naver import NaverNewsClient
from app.clients.openai import OpenAIClient
from app.clients.s3 import BotoS3Client, S3Client
from app.clients.whisper import (
    OpenAIWhisperClient,
    WhisperClient,
)
from app.core.config import (
    Settings,
    get_settings,
)
from app.core.errors import (
    BusinessException,
    CommonErrorCode,
)


@lru_cache
def _openai_client(
    api_key: str,
    base_url: str,
) -> OpenAIClient:
    return OpenAIClient(
        api_key=api_key,
        base_url=base_url or None,
    )


@lru_cache
def _anthropic_client(
    api_key: str,
) -> AnthropicClient:
    return AnthropicClient(
        api_key=api_key,
    )


@lru_cache
def _whisper_client(
    api_key: str,
) -> OpenAIWhisperClient:
    return OpenAIWhisperClient(
        api_key=api_key,
    )


@lru_cache
def _s3_client(
    bucket: str,
    region: str,
    access_key: str,
    secret_key: str,
) -> BotoS3Client:
    return BotoS3Client(
        bucket=bucket,
        region=region,
        access_key=access_key,
        secret_key=secret_key,
    )


@lru_cache
def _gpu_client(
    url: str,
) -> GpuServerClient:
    return GpuServerClient(
        base_url=url,
    )


def get_openai_client(
    settings: Settings = Depends(get_settings),
) -> OpenAIClient:
    return _openai_client(
        settings.OPENAI_API_KEY,
        settings.OPENAI_BASE_URL,
    )


def get_anthropic_client(
    settings: Settings = Depends(get_settings),
) -> AnthropicClient:
    return _anthropic_client(
        settings.ANTHROPIC_API_KEY,
    )


def get_llm_client(
    settings: Settings = Depends(get_settings),
) -> LLMClient:
    if settings.ANTHROPIC_API_KEY:
        return _anthropic_client(
            settings.ANTHROPIC_API_KEY,
        )

    return _openai_client(
        settings.OPENAI_API_KEY,
        settings.OPENAI_BASE_URL,
    )


def get_whisper_client(
    settings: Settings = Depends(get_settings),
) -> WhisperClient:
    return _whisper_client(
        settings.OPENAI_API_KEY,
    )


def get_s3_client(
    settings: Settings = Depends(get_settings),
) -> S3Client:
    return _s3_client(
        settings.S3_BUCKET,
        settings.S3_REGION,
        settings.AWS_ACCESS_KEY_ID,
        settings.AWS_SECRET_ACCESS_KEY,
    )


def get_gpu_client(
    settings: Settings = Depends(get_settings),
) -> GpuServerClient:
    if not settings.GPU_SERVER_URL:
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            "GPU_SERVER_URL 환경변수가 설정되지 않았습니다",
        )

    return _gpu_client(
        settings.GPU_SERVER_URL,
    )


@lru_cache
def _naver_client(
    client_id: str,
    client_secret: str,
) -> NaverNewsClient:
    return NaverNewsClient(
        client_id=client_id,
        client_secret=client_secret,
    )


def get_naver_client(
    settings: Settings = Depends(get_settings),
) -> NaverNewsClient:
    if not settings.NAVER_CLIENT_ID or not settings.NAVER_CLIENT_SECRET:
        raise BusinessException(
            CommonErrorCode.INVALID_INPUT,
            "NAVER_CLIENT_ID / NAVER_CLIENT_SECRET 환경변수가 설정되지 않았습니다",
        )
    return _naver_client(
        settings.NAVER_CLIENT_ID,
        settings.NAVER_CLIENT_SECRET,
    )


_embedder: OnnxEmbedderClient | None = None


def get_embedder_client(
    settings: Settings = Depends(get_settings),
) -> OnnxEmbedderClient | None:
    return _embedder


def warmup_embedder_client(
    model_path: str,
) -> OnnxEmbedderClient:
    global _embedder

    if _embedder is None:
        _embedder = OnnxEmbedderClient(
            model_path,
        )

    return _embedder