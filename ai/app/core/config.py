from functools import lru_cache

from pydantic_settings import (
    BaseSettings,
    SettingsConfigDict,
)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file="../.env.local",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    APP_ENV: str = "local"
    LOG_LEVEL: str = "DEBUG"

    # ─────────────────────────────────────────────
    # Database
    # ─────────────────────────────────────────────

    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_USER: str = "fint"
    POSTGRES_PASSWORD: str = "fint"
    POSTGRES_DB: str = "fint_local"

    # ─────────────────────────────────────────────
    # Redis
    # ─────────────────────────────────────────────

    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_PASSWORD: str = ""

    # ─────────────────────────────────────────────
    # Auth
    # ─────────────────────────────────────────────

    JWT_SECRET: str = ""

    # ─────────────────────────────────────────────
    # OpenAI / GMS
    # ─────────────────────────────────────────────

    OPENAI_API_KEY: str = ""

    OPENAI_BASE_URL: str = (
        "https://gms.ssafy.io/gmsapi/api.openai.com/v1"
    )

    OPENAI_OCR_MODEL: str = "gpt-5-nano"

    # ─────────────────────────────────────────────
    # Anthropic
    # ─────────────────────────────────────────────

    ANTHROPIC_API_KEY: str = ""

    # ─────────────────────────────────────────────
    # Embedding
    # ─────────────────────────────────────────────

    EMBEDDING_MODEL_PATH: str = "model/e5-small"

    # ─────────────────────────────────────────────
    # GPU OCR Server
    # ─────────────────────────────────────────────

    GPU_SERVER_URL: str = ""

    # 예:
    # http://gpu-server:8002

    # ─────────────────────────────────────────────
    # AWS S3
    # ─────────────────────────────────────────────

    S3_BUCKET: str = "fint-local"
    S3_REGION: str = "ap-northeast-2"

    AWS_ACCESS_KEY_ID: str = ""
    AWS_SECRET_ACCESS_KEY: str = ""

    # ─────────────────────────────────────────────
    # URLs
    # ─────────────────────────────────────────────

    @property
    def database_url(self) -> str:
        return (
            f"postgresql+asyncpg://"
            f"{self.POSTGRES_USER}:"
            f"{self.POSTGRES_PASSWORD}"
            f"@{self.POSTGRES_HOST}:"
            f"{self.POSTGRES_PORT}/"
            f"{self.POSTGRES_DB}"
        )

    @property
    def redis_url(self) -> str:
        if self.REDIS_PASSWORD:
            return (
                f"redis://:{self.REDIS_PASSWORD}"
                f"@{self.REDIS_HOST}:{self.REDIS_PORT}/0"
            )

        return (
            f"redis://"
            f"{self.REDIS_HOST}:"
            f"{self.REDIS_PORT}/0"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()