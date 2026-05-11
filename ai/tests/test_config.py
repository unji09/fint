from app.core.config import Settings


def test_default_settings():
    settings = Settings(
        _env_file=None,
        POSTGRES_HOST="localhost",
        POSTGRES_PORT=5432,
        POSTGRES_USER="fint",
        POSTGRES_PASSWORD="fint",
        POSTGRES_DB="fint_local",
    )

    assert settings.APP_ENV == "local"
    assert settings.LOG_LEVEL == "DEBUG"
    assert settings.POSTGRES_HOST == "localhost"


def test_database_url_format():
    settings = Settings(
        _env_file=None,
        POSTGRES_HOST="db.example.com",
        POSTGRES_PORT=5433,
        POSTGRES_USER="myuser",
        POSTGRES_PASSWORD="mypass",
        POSTGRES_DB="mydb",
    )

    assert settings.database_url == ("postgresql+asyncpg://myuser:mypass@db.example.com:5433/mydb")


def test_redis_url_without_password():
    settings = Settings(
        _env_file=None,
        REDIS_HOST="localhost",
        REDIS_PORT=6379,
        REDIS_PASSWORD="",
    )

    assert settings.redis_url == "redis://localhost:6379/0"


def test_redis_url_with_password():
    settings = Settings(
        _env_file=None,
        REDIS_HOST="redis.example.com",
        REDIS_PORT=6380,
        REDIS_PASSWORD="secret",
    )

    assert settings.redis_url == "redis://:secret@redis.example.com:6380/0"
