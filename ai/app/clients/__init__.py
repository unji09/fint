from fastapi import Depends

from app.core.config import Settings, get_settings

from app.clients.llm import LLMClient
from app.clients.openai import OpenAIClient
from app.clients.anthropic import AnthropicClient
from app.clients.whisper import WhisperClient, OpenAIWhisperClient
from app.clients.s3 import S3Client, BotoS3Client


def get_openai_client(settings: Settings = Depends(get_settings)) -> OpenAIClient:
    return OpenAIClient(api_key=settings.OPENAI_API_KEY)


def get_anthropic_client(settings: Settings = Depends(get_settings)) -> AnthropicClient:
    return AnthropicClient(api_key=settings.ANTHROPIC_API_KEY)


def get_llm_client(settings: Settings = Depends(get_settings)) -> LLMClient:
    if settings.ANTHROPIC_API_KEY:
        return AnthropicClient(api_key=settings.ANTHROPIC_API_KEY)
    return OpenAIClient(api_key=settings.OPENAI_API_KEY)


def get_whisper_client(settings: Settings = Depends(get_settings)) -> WhisperClient:
    return OpenAIWhisperClient(api_key=settings.OPENAI_API_KEY)


def get_s3_client(settings: Settings = Depends(get_settings)) -> S3Client:
    return BotoS3Client(
        bucket=settings.S3_BUCKET,
        region=settings.S3_REGION,
        access_key=settings.AWS_ACCESS_KEY_ID,
        secret_key=settings.AWS_SECRET_ACCESS_KEY,
    )
