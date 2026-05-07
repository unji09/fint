from functools import lru_cache

from fastapi import Depends

from app.clients.anthropic import AnthropicClient
from app.clients.llm import LLMClient
from app.clients.ocr import OcrClient, RapidOcrClient
from app.clients.openai import OpenAIClient
from app.clients.s3 import BotoS3Client, S3Client
from app.clients.whisper import OpenAIWhisperClient, WhisperClient
from app.core.config import Settings, get_settings


@lru_cache
def _openai_client(api_key: str, base_url: str) -> OpenAIClient:
    return OpenAIClient(api_key=api_key, base_url=base_url or None)


@lru_cache
def _anthropic_client(api_key: str) -> AnthropicClient:
    return AnthropicClient(api_key=api_key)


@lru_cache
def _whisper_client(api_key: str) -> OpenAIWhisperClient:
    return OpenAIWhisperClient(api_key=api_key)


@lru_cache
def _s3_client(bucket: str, region: str, access_key: str, secret_key: str) -> BotoS3Client:
    return BotoS3Client(bucket=bucket, region=region, access_key=access_key, secret_key=secret_key)


@lru_cache
def _ocr_client() -> RapidOcrClient:
    return RapidOcrClient()


def get_openai_client(settings: Settings = Depends(get_settings)) -> OpenAIClient:
    return _openai_client(settings.OPENAI_API_KEY, settings.OPENAI_BASE_URL)


def get_anthropic_client(settings: Settings = Depends(get_settings)) -> AnthropicClient:
    return _anthropic_client(settings.ANTHROPIC_API_KEY)


def get_llm_client(settings: Settings = Depends(get_settings)) -> LLMClient:
    if settings.ANTHROPIC_API_KEY:
        return _anthropic_client(settings.ANTHROPIC_API_KEY)
    return _openai_client(settings.OPENAI_API_KEY, settings.OPENAI_BASE_URL)


def get_whisper_client(settings: Settings = Depends(get_settings)) -> WhisperClient:
    return _whisper_client(settings.OPENAI_API_KEY)


def get_s3_client(settings: Settings = Depends(get_settings)) -> S3Client:
    return _s3_client(
        settings.S3_BUCKET,
        settings.S3_REGION,
        settings.AWS_ACCESS_KEY_ID,
        settings.AWS_SECRET_ACCESS_KEY,
    )


def get_ocr_client() -> OcrClient:
    return _ocr_client()


def warmup_ocr_client() -> RapidOcrClient:
    return _ocr_client()
