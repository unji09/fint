import pytest
from unittest.mock import patch

from app.clients import get_llm_client, get_openai_client, get_anthropic_client, get_whisper_client, get_s3_client
from app.clients.openai import OpenAIClient
from app.clients.anthropic import AnthropicClient
from app.clients.whisper import OpenAIWhisperClient
from app.clients.s3 import BotoS3Client
from app.clients.llm import LLMClient
from app.clients.whisper import WhisperClient
from app.clients.s3 import S3Client
from app.core.config import Settings
from app.core.errors import BusinessException


def _settings(**overrides) -> Settings:
    defaults = dict(
        _env_file=None,
        OPENAI_API_KEY="sk-test",
        ANTHROPIC_API_KEY="ant-test",
        S3_BUCKET="test-bucket",
        S3_REGION="us-east-1",
        AWS_ACCESS_KEY_ID="AKIA_TEST",
        AWS_SECRET_ACCESS_KEY="secret",
    )
    defaults.update(overrides)
    return Settings(**defaults)


def test_get_openai_client_returns_openai():
    client = get_openai_client(settings=_settings())
    assert isinstance(client, OpenAIClient)


def test_get_anthropic_client_returns_anthropic():
    client = get_anthropic_client(settings=_settings())
    assert isinstance(client, AnthropicClient)


def test_get_llm_client_prefers_anthropic_when_both_keys():
    client = get_llm_client(settings=_settings())
    assert isinstance(client, AnthropicClient)


def test_get_llm_client_falls_back_to_openai():
    client = get_llm_client(settings=_settings(ANTHROPIC_API_KEY=""))
    assert isinstance(client, OpenAIClient)


def test_get_whisper_client_returns_openai_whisper():
    client = get_whisper_client(settings=_settings())
    assert isinstance(client, OpenAIWhisperClient)


def test_get_s3_client_returns_boto():
    client = get_s3_client(settings=_settings())
    assert isinstance(client, BotoS3Client)


def test_openai_client_raises_without_key():
    with pytest.raises(BusinessException) as exc_info:
        OpenAIClient(api_key="")
    assert exc_info.value.error_code.code == "C502"


def test_anthropic_client_raises_without_key():
    with pytest.raises(BusinessException) as exc_info:
        AnthropicClient(api_key="")
    assert exc_info.value.error_code.code == "C502"


def test_openai_client_satisfies_llm_protocol():
    client = OpenAIClient(api_key="sk-test")
    assert isinstance(client, LLMClient)


def test_anthropic_client_satisfies_llm_protocol():
    client = AnthropicClient(api_key="ant-test")
    assert isinstance(client, LLMClient)


def test_whisper_client_satisfies_protocol():
    client = OpenAIWhisperClient(api_key="sk-test")
    assert isinstance(client, WhisperClient)


def test_s3_client_satisfies_protocol():
    client = BotoS3Client(
        bucket="b", region="us-east-1", access_key="ak", secret_key="sk"
    )
    assert isinstance(client, S3Client)
