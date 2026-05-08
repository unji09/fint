FROM python:3.12-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /app
COPY pyproject.toml uv.lock* ./
RUN uv sync --frozen --no-install-project --no-dev

COPY . .
RUN uv sync --frozen --no-dev
RUN mkdir -p /app/model


FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Seoul
WORKDIR /app

COPY --from=builder /app/.venv /app/.venv
COPY --from=builder /app/app /app/app
COPY --from=builder /app/model /app/model

ENV PATH="/app/.venv/bin:$PATH"

RUN python -c "from rapidocr import RapidOCR, LangDet, LangRec, OCRVersion, ModelType; RapidOCR(params={'Det.lang_type': LangDet.CH, 'Det.ocr_version': OCRVersion.PPOCRV5, 'Det.model_type': ModelType.MOBILE, 'Rec.lang_type': LangRec.KOREAN, 'Rec.ocr_version': OCRVersion.PPOCRV5, 'Rec.model_type': ModelType.MOBILE})"

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD curl -sf http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
