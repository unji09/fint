# F!NT AI Service

Spring Boot에서 호출하는 stateless FastAPI 서버. LLM, STT, NLP 처리를 담당한다.

## Quickstart

```bash
# 환경변수 설정 (프로젝트 루트에서)
cp .env.local.example .env.local

# 로컬 인프라 기동 (PostgreSQL + Redis)
make up

# 의존성 설치
cd ai
uv sync

# 서버 기동 (reload 모드)
uv run uvicorn app.main:create_app --factory --reload --port 8000

# 테스트
uv run pytest -v
```

## 프로젝트 구조

```
app/
├── main.py             # App factory + lifespan + 라우터 등록
├── core/               # 공통 인프라 (Depends 체인 뿌리)
│   ├── config.py       # Settings (pydantic-settings)
│   ├── db.py           # get_db() 의존성
│   ├── redis.py        # get_redis() 의존성
│   ├── security.py     # get_tenant_id() 의존성
│   ├── errors.py       # ErrorCode + BusinessException
│   └── response.py     # ApiResponse[T]
├── clients/            # 외부 API 클라이언트 (Depends로 주입)
│   ├── llm.py          # LLMClient Protocol + factory
│   ├── whisper.py      # WhisperClient + factory
│   └── s3.py           # S3Client + factory
├── schemas/            # Pydantic 모델 (API 계약)
│   └── stt.py
└── routers/            # 엔드포인트
    ├── health.py
    └── stt.py
```

## 새 도메인 추가 방법

1. `app/schemas/<domain>.py` — Request/Response Pydantic 모델
2. `app/routers/<domain>.py` — APIRouter + Depends() 주입
3. `app/main.py` — `app.include_router(<domain>.router)` 한 줄 추가
4. `tests/routers/test_<domain>.py` — 테스트 작성

## 환경변수

루트 `.env.local` (로컬) / `.env.dev` (배포)에서 통합 관리. AI 서비스가 사용하는 변수:

| 변수 | 설명 |
|------|------|
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `POSTGRES_PORT` | DB 연결 (read-only) |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 연결 |
| `JWT_SECRET` | JWT 검증 |
| `OPENAI_API_KEY` | OpenAI API |
| `ANTHROPIC_API_KEY` | Anthropic API |
| `S3_BUCKET`, `S3_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | S3 파일 읽기 |

## Make 타겟

```bash
make ai          # 로컬 서버 기동 (reload)
make ai-test     # 테스트 실행
make ai-lint     # ruff 린트 체크
make ai-format   # ruff 자동 포맷
make ai-docker   # Docker 이미지 빌드
```
