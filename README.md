# 💡 F!NT (핀트)

### 지능형 영업관리를 위한 인공지능 B2B CRM

> *"기록하는 CRM이 아니라, 행동을 만들어내는 CRM"*

뉴스 · 공시 · 미팅 이력을 자동 수집 · 구조화하고, 영업사원에게 다음 행동(Next Best Action)을 제시하는 B2B CRM.

![랜딩 페이지](./docs/images/landing.png)

**SSAFY 14기 자율 PJT** · Team A301 · 철철이형팬클럽 · 2026.04 ~

[🌐 데모 (준비 중)](#) | [🪧 Jira 보드](https://ssafy.atlassian.net/jira/software/projects/S14P31A301) | [🐛 GitLab Repository](https://lab.ssafy.com/s14-final/S14P31A301)

---

## 📋 목차

- [프로젝트 소개](#-프로젝트-소개)
- [주요 기능](#-주요-기능)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [ERD](#-erd)
- [시작하기](#-시작하기)
- [API 문서](#-api-문서)
- [프로젝트 구조](#-프로젝트-구조)
- [주요 화면](#-주요-화면)
- [개발 가이드](#-개발-가이드)
- [배포](#-배포)
- [프로젝트 통계](#-프로젝트-통계)
- [팀 소개](#-팀-소개)
- [참고 자료](#-참고-자료)

---

## 🎯 프로젝트 소개

### 왜 F!NT인가?

> "열심히 하는데, 뭘 하고 있는 건지 모르겠다."

B2B 영업 현장의 핵심 문제:

1. **전략 수립 정보 마이닝에 과도한 시간 소요** — 5~6개 소스(뉴스 · DART · 메일 · 캘린더 · 드라이브)를 수동으로 뒤져야 함
2. **영업 기회 타이밍을 사후에 인지** — 실시간 시그널 감지 체계 부재
3. **현장 영업사원의 관계 정보가 조직 자산으로 연결되지 않음** — 담당자 퇴사 시 관계 자산 소멸

F!NT는 **자동 수집 → 위키 구조화 → 인사이트 도출 → 액션 추천**의 흐름으로 영업 데이터를 "행동"으로 변환한다.

### 핵심 가치

- 🤖 **자동 수집** — 명함 OCR · 미팅 STT · 뉴스/DART API · 캘린더 · 이메일 OAuth
- 📚 **위키 기반 누적** — 고객사(Account) · 담당자(Contact) 위키 자동 생성 및 업데이트
- 🎯 **Next Best Action** — AI가 추천하고, 사람이 결정 (HMI 원칙)
- 🗣️ **자연어 대시보드** — "이번 주 바로 연락해야 할 고객 보여줘" → 위젯 자동 생성
- 🔒 **멀티테넌트 격리** — `tenant_id` 필터 Interceptor 강제, 타 테넌트 접근 시 404 (존재 은닉)

### 타겟 페르소나

> 26세 · 영업사원 입사 3개월차 · 전임자 퇴사로 고객사 5개를 한꺼번에 인수인계받은 민철수

| 타겟 | 설명 |
|------|------|
| **1차** | B2B 영업사원 (입사 초기~3년차) — 인수인계 직후 맥락 파악이 어려운 담당자 |
| **2차** | 영업 팀장 — 팀원별 파이프라인 현황과 리스크를 한눈에 보고 싶은 팀장 |
| **3차** | 관리자(Admin) — 전사 고객 데이터 자산화와 인수인계 비용 절감 필요 |

---

## ✨ 주요 기능

F!NT는 **3 Layer** 구조로 영업 데이터를 처리한다.

### Layer 1. 데이터 수집 (Input)

![데이터 수집](./docs/images/feature-input.png)

| 구분 | 수집 방식 | 핵심 기능 |
|------|----------|----------|
| 고객 정보 | 명함 촬영 | OCR 기반 Contact 자동 등록 |
| 미팅 내용 | 앱 녹음 / 파일 업로드 | STT 변환 + 핵심 요약 + 엔티티 자동 추출 |
| 외부 동향 | API 스케줄러 | 뉴스 주기 수집 + DART 공시 수집 |
| 일정 | Google Calendar 연동 | OAuth 기반 예정 미팅 자동 파악 |
| 이메일 | OAuth / .eml 업로드 | 이메일 내용을 Activity로 자동 반영 |

### Layer 2. 데이터 구조화 (Knowledge)

![지식 구조화](./docs/images/feature-knowledge.png)

**고객사(Account) 위키 — 자동 생성 및 누적**

| 카테고리 | 내용 |
|---------|------|
| 기본 정보 | 기업 개요, 업종, 사업자번호 |
| 재무/동향 | DART 공시, 뉴스 기반 최신 이벤트 |
| 영업 히스토리 | 미팅 · 계약 · 이슈 타임라인 |
| 리스크 | 산업/경영 리스크 |
| 고객 온도 | 관계 온도 추이 (RAINBOW · SUNNY · CLOUDY · RAINY · THUNDER) |

**담당자(Contact) 위키**

| 카테고리 | 내용 |
|---------|------|
| 기본 정보 | 이름, 직책, 연락처 |
| 성향/선호 | 의사결정 스타일, 관심사, 커뮤니케이션 성향 |
| 관계 정보 | 이해관계자 맵, Contact 간 관계 |
| 미팅 이력 | 누적 미팅 요약, 합의 사항 |

**미팅 전 자동 브리핑 (3가지 핵심 항목)**
1. "이 고객 왜 중요한지"
2. "최근 이슈 무엇인지"
3. "이번 미팅에서 볼 포인트 / 추천 어젠다"

### Layer 3. 행동 유도 (Action)

![액션 추천](./docs/images/feature-action.png)

**자연어 기반 맞춤형 대시보드**
- 자연어 입력 → 위젯 자동 생성 (SSE 스트리밍)
- 위젯 드래그 배치 / 저장 / 멀티 대시보드 전환
- 시스템 제공 템플릿 (Next Action · Pipeline 리스크 · 고객 인사이트 · 매출 시뮬레이션 · 세그먼트)

**AI 추천**

| 추천 유형 | 내용 |
|----------|------|
| Next Action | "지금 연락해야 할 고객", "재접촉 타이밍" |
| 영업 시그널 알림 | 고객사 투자 발표 · 임원 교체 등 자동 감지 → 실시간 알림 |
| 고객 인사이트 | "이 고객은 지금 자금 니즈가 발생할 가능성이 높다" |
| 우선순위 정렬 | 고객별 중요도 자동 정렬, 딜 가능성 기반 추천 |

**영업건(Deal) 파이프라인**

| 스테이지 | 컬러 |
|---------|------|
| 첫 미팅 준비 → 니즈 파악 → 제안 작성 → 제안 발표 → 협상 중 → 계약 검토 → 성사/실패 | 단계별 색상 토큰 정의 |

> **핵심 원칙: HMI (Human-Machine Interaction)** — AI는 추천하고, 최종 판단과 책임은 사람.

---

## 🛠 기술 스택

### Frontend

| 영역 | 스택 | 비고 |
|------|------|------|
| Web | **Next.js 14** (App Router) · TypeScript · React 18 | `pnpm` |
| Android | **Kotlin 네이티브** | `minSdkVersion 35` (Android 15 / OneUI 7 이상) |
| 차트 | Chart.js · react-chartjs-2 | |
| 실시간 | `@microsoft/fetch-event-source` (SSE) · `@stomp/stompjs` (WebSocket) | |
| 테스트 | Vitest · @testing-library/react · jsdom | |

웹 · 앱은 **동일 API**를 사용하며, 기능/권한 차이가 없도록 설계한다.

### Backend

| 영역 | 스택 | 역할 |
|------|------|------|
| Main API | **Spring Boot 4.0.5** (Java 21, Gradle 8.14) | 비즈니스 로직, 인증(JWT), CRUD, 파일 메타, 스케줄링, 외부 데이터 수집(DART/뉴스) |
| AI Service | **FastAPI** (Python, uv) | stateless — LLM · RAG · 스코어링 · STT · 화자 분리 |
| Security | Spring Security + JWT (`jjwt 0.12.6`) | |
| Migration | **Flyway** | `ddl-auto: update` 금지 |
| Test | Testcontainers, JUnit 5 | |
| Metrics | Micrometer + Prometheus | |
| Docs | Springdoc OpenAPI 3 | |
| AWS | AWS SDK v2 (S3) | Pre-signed URL 방식 업로드 |

> Spring → FastAPI **일방향 호출**. FastAPI는 외부 노출 없음 (내부망 전용).

### Data

| 영역 | 스택 | 역할 |
|------|------|------|
| Master DB | **PostgreSQL 16** (+ pgvector) | 비즈니스 마스터 + 뉴스/공시 임베딩 |
| Cache / Queue | **Redis 7** | AT 블랙리스트 · LLM 응답 캐시 · Stream(Spring → FastAPI 비동기) |
| Object Storage | **AWS S3** | 녹음 · 문서 · 이미지 (Pre-signed URL) |

데이터 정책:
- **PostgreSQL 쓰기는 Spring만, 읽기는 Spring + FastAPI**
- 정형 필드는 컬럼, 가변 부가정보는 **JSONB** 하이브리드
- 캐시 키에 `tenant_id` 필수 포함
- Refresh Token은 PostgreSQL에 영구 저장 (rotation 이력 + 탈취 감지)

### Infra & DevOps

| 영역 | 스택 |
|------|------|
| Cloud | AWS Lightsail XL (4 vCPU / 16GB / 320GB SSD) 단일 인스턴스 |
| Container | Docker · Docker Compose |
| Reverse Proxy | **Nginx** (단일 진입점, FastAPI는 내부망 전용) |
| CI/CD | GitLab → Jenkins → Docker Image → 배포 |
| Monitoring | **Prometheus** + **Loki** + **Grafana** + Alertmanager (Mattermost webhook) |
| Local | Make 기반 통합 명령 (`make doctor`, `make up`, `make backend`) |

### 환경 분리

| 프로파일 | 용도 | DB |
|---------|------|-----|
| `local` | 개발자 PC | Docker PostgreSQL · Redis |
| `dev` | EC2 배포 서버 | Docker Compose PostgreSQL · Redis |
| `test` | 테스트 | Testcontainers (자동 기동) |

---

## 🏗 시스템 아키텍처

### 전체 구조도

```
┌──────────────────────────────────────────────────────────────┐
│                       Client Layer                           │
│   ┌────────────────┐                  ┌───────────────────┐  │
│   │  Next.js Web   │                  │  Android Native   │  │
│   │   (App Router) │                  │     (Kotlin)      │  │
│   └────────┬───────┘                  └─────────┬─────────┘  │
└────────────┼────────────────────────────────────┼────────────┘
             │       HTTPS / SSE / WebSocket      │
             └──────────────┬─────────────────────┘
                            │
┌───────────────────────────┼──────────────────────────────────┐
│                           ▼                                  │
│   ┌──────────────────────────────────────────────────────┐   │
│   │      Nginx (Reverse Proxy · 단일 진입점 · TLS)        │   │
│   └────────┬──────────────────────────────────┬──────────┘   │
│            │                                  │              │
│            ▼  (외부)                  (내부망)▼              │
│   ┌──────────────────┐              ┌──────────────────┐     │
│   │  Spring Boot     │              │   FastAPI        │     │
│   │   (Main API)     │ ───────────► │   (AI · STT)     │     │
│   │                  │ 일방향 호출    │   stateless      │     │
│   │ auth/tenant      │              │                  │     │
│   │ customer/deal    │              │ LLM / RAG /      │     │
│   │ meeting/signal   │              │ 스코어링 / Whisper │     │
│   │ dashboard        │              │                  │     │
│   └────┬───────┬─────┘              └────┬───────┬─────┘     │
│        │       │  (Redis Stream)         │       │           │
│        │       └────────────────►────────┘       │           │
└────────┼────────────────────────────────────────┼────────────┘
         │                                         │
┌────────┼─────────────────────────────────────────┼───────────┐
│        ▼               Data Layer                ▼           │
│   ┌──────────────┐      ┌──────────────┐    ┌─────────────┐  │
│   │  PostgreSQL  │      │    Redis     │    │   AWS S3    │  │
│   │  (+pgvector) │      │              │    │             │  │
│   │              │      │ - AT블랙리스트 │    │ - 녹음 원본   │  │
│   │ - 비즈니스 마스터│   │ - LLM 캐시    │    │ - 문서/이미지 │  │
│   │ - 뉴스/공시   │     │ - 비동기 큐   │     │ - Pre-signed │  │
│   │ - 임베딩(384) │     │              │    │   URL 업로드 │  │
│   └──────────────┘      └──────────────┘    └─────────────┘  │
│      쓰기: Spring        Spring + FastAPI       Spring 발급   │
│      읽기: Spring+FastAPI    양방향           FastAPI는 boto3 │
└──────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼────────────────────────────────┐
│                             ▼                                │
│  ┌────────────┐   ┌──────────────┐   ┌──────────────────┐    │
│  │  DART API  │   │  News API    │   │  LLM (OpenAI/    │    │
│  │  (Spring)  │   │ + 크롤링      │   │   Anthropic)     │    │
│  │            │   │  (Spring)    │   │    (FastAPI)     │    │
│  └────────────┘   └──────────────┘   └──────────────────┘    │
│                                                              │
│  ┌────────────┐   ┌──────────────┐   ┌──────────────────┐    │
│  │    FCM     │   │ Google       │   │  Whisper (STT)   │    │
│  │  (Spring)  │   │  Calendar    │   │     (FastAPI)    │    │
│  └────────────┘   └──────────────┘   └──────────────────┘    │
│                    External Services                         │
└──────────────────────────────────────────────────────────────┘
```

상세 다이어그램: [docs/images/architecture.png](docs/images/architecture.png) · [Draw.io](https://drive.google.com/file/d/16TekfqxYtelc9bR-hm7cTv0CNRSkxOSR/view)

### 아키텍처 핵심 원칙

1. **FastAPI는 stateless** — 필요한 데이터는 Spring이 payload로 전달하거나 FastAPI가 PostgreSQL에서 직접 읽음
2. **Nginx → Spring 단일 진입** — FastAPI는 내부망 전용
3. **파일 업로드는 Pre-signed URL** — 서버 경유 업로드 금지. URL 발급은 Spring 단일 주체
4. **`tenant_id` 필터링을 Interceptor로 강제** — Spring · FastAPI 양쪽
5. **LLM 응답은 Redis 캐싱** — 캐시 키에 `tenant_id` 포함
6. **Spring → FastAPI 호출**은 타임아웃 · 재시도 · circuit breaker 명시

### 주요 데이터 플로우

#### 1. 인증 플로우
```
Client → Spring (/auth/login) → PostgreSQL (User 검증)
→ Access Token 발급 (Authorization 헤더)
→ Refresh Token 발급 → PostgreSQL 저장 (rotation 추적)
→ Client (토큰 저장)
```

#### 2. 미팅 STT 처리 플로우 (비동기)
```
1. Client → Spring (POST /files/multipart/init) → S3 Pre-signed URL
2. Client → S3 (직접 업로드)
3. Client → Spring (POST /activities/{id}/recording) → 202 Accepted
4. Spring → Redis Stream → FastAPI (STT + 요약 + 엔티티 추출)
5. FastAPI → PostgreSQL (transcript/summary 직접 쓰기는 Spring 콜백)
6. Client → Spring (GET /activities/{id} 폴링, 5초 간격)
   sttStatus: NONE → PENDING → PROCESSING → COMPLETED | FAILED
```

#### 3. 자연어 대시보드 쿼리 플로우 (SSE)
```
1. Client → Spring (POST /dashboards/{id}/queries) → { traceId }
2. Client → Spring (GET /dashboards/queries/{traceId}/stream, SSE)
3. Spring → FastAPI → LLM (자연어 → 위젯 스키마)
4. SSE 이벤트: progress (4단계) → complete | error
5. Client (위젯 자동 추가)
```

#### 4. 영업 시그널 자동 감지 플로우
```
Scheduler → Spring (News/DART API 주기 수집)
→ PostgreSQL (news_articles, dart_disclosures + 임베딩)
→ FastAPI (이벤트 분류 + 영업 시그널 추출)
→ Spring (account_news_articles, account_dart_disclosures 연결)
→ AI Suggestion 생성 → FCM/in-app 알림
```

---

## 📊 ERD

> 전체 ERD: [docs/erd.md](docs/erd.md) · 다이어그램: [docs/images/erd.png](docs/images/erd.png)

### 도메인별 엔티티 그룹

**조직 · 인증**
- `tenants` — 서비스 계약 단위(회사). **데이터 격리 키**
- `users` — 사용자 계정 (role: ADMIN · MANAGER · MEMBER · DEVELOPER)
- `teams` — 조직 단위

**고객 · 영업**
- `accounts` — 고객사
- `contacts` — 고객 담당자
- `account_user_assignment` — 고객사-담당사원 배정
- `deals` — 영업건
- `pipeline_stages` — 딜 파이프라인 스테이지 (tenant별)
- `deal_contacts` — 딜-담당자 연결

**활동 · 미팅**
- `activities` — 활동(미팅/통화/메모/이메일) · STT transcript · AI summary
- `files` — S3 파일 메타
- `email_oauth_tokens` — 이메일 OAuth 토큰

**시그널 · AI**
- `news_articles` — 뉴스 (vector(384) 임베딩)
- `dart_disclosures` — DART 공시 (vector(384) 임베딩)
- `account_news_articles` / `account_dart_disclosures` — 고객사-시그널 연결
- `ai_suggestions` — Next Action 추천
- `temperature_history` — 고객 온도 추이 (시계열)

**대시보드**
- `dashboards` — 사용자 생성 커스텀 대시보드
- `dashboard_widgets` — 위젯 (배치 좌표 JSONB)
- `dashboard_templates` — 시스템 제공 템플릿
- `dashboard_queries` — 자연어 쿼리 이력

### 주요 ENUM

| 도메인 | ENUM | 값 |
|--------|------|----|
| Role | `role` | `ADMIN` · `MANAGER` · `MEMBER` · `DEVELOPER` |
| Mood | `mood` | `RAINBOW`(최고) · `SUNNY`(좋음) · `CLOUDY`(보통) · `RAINY`(나쁨) · `THUNDER`(최악) |
| Activity Type | `type` | `MEETING` · `CALL` · `MEMO` · `EMAIL` |
| STT 상태 | `stt_status` | `NONE` · `PENDING` · `PROCESSING` · `COMPLETED` · `FAILED` |
| Calendar Source | `source` | `GOOGLE` · `FINT` |
| Widget Type | `widget_type` | `BAR_CHART` · `LINE` · `PIE` · `KPI` · `TABLE` |

### 멀티테넌트 격리

- **모든 PostgreSQL 쿼리**(Spring · FastAPI 공통)에 `tenant_id` 필터 강제
- Controller / FastAPI Endpoint에서 `tenant_id` 직접 읽지 않음 — `SecurityContext` / Interceptor / Dependency 통해 주입
- Spring → FastAPI 호출 시 `tenant_id`는 JWT 또는 내부 헤더로 전달, FastAPI도 재검증
- 타 테넌트 데이터 접근 시 **404 반환** (존재 은닉)

---

## 🚀 시작하기

### 전제 조건

- **Docker Desktop** (Windows/Mac) 또는 Docker Engine + Compose plugin (Linux)
- **Git**
- **make** — Windows는 `scoop install make` 또는 `choco install make`
- JDK 21은 Gradle이 자동 다운로드 (수동 설치 불필요)
- (웹 개발 시) **Node.js 20+** · `pnpm`
- (AI 서비스 개발 시) **Python 3.12+** · `uv`

### 퀵스타트 (Make)

```bash
git clone https://lab.ssafy.com/s14-final/S14P31A301.git
cd S14P31A301
git checkout dev

make doctor    # 환경 검사 + .env 자동 생성
make up        # PostgreSQL + Redis 기동
make backend   # Spring Boot 기동 (별도 터미널)
```

기동 확인:
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Actuator Health**: http://localhost:8080/actuator/health
- **Prometheus 메트릭**: http://localhost:8080/actuator/prometheus

### 주요 make 명령

| 명령 | 설명 |
|------|------|
| `make doctor` | 환경 사전 검사 (Docker · 포트 · JDK · .env) |
| `make up` | PostgreSQL + Redis 기동 |
| `make down` | PostgreSQL + Redis 정지 |
| `make backend` | Spring Boot 기동 (local 프로파일) |
| `make ai` | FastAPI 기동 (reload 모드) |
| `make ai-test` | FastAPI 테스트 실행 |
| `make logs` | 인프라 컨테이너 로그 |
| `make status` | 컨테이너 상태 + Spring 헬스체크 |
| `make clean` | DB 볼륨 포함 전체 초기화 |
| `make monitoring-up` | Prometheus + Grafana + Loki 기동 |

### 프론트엔드 웹 실행

```bash
cd frontend-web
pnpm install
pnpm dev          # http://localhost:3000
pnpm typecheck    # TS 타입 체크
pnpm test         # Vitest
```

### AI 서비스 실행

```bash
cd ai
uv sync
uv run uvicorn app.main:create_app --factory --reload --port 8000
uv run pytest -v
```

- **FastAPI Docs**: http://localhost:8000/docs

### 모니터링 스택 (선택)

```bash
make monitoring-up
```

- Grafana: http://localhost:3001
- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093

상세: [infra/monitoring/README.md](infra/monitoring/README.md) · [docs/monitoring.md](docs/monitoring.md)

### 수동 설정 (Make 없이)

```bash
# 데이터 스택 기동
cd infra && cp .env.example .env && docker compose up -d

# 백엔드 기동
cd backend && cp .env.example .env && \
  ./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## 📚 API 문서

### Swagger UI

전체 API 명세는 Swagger UI에서 확인:

👉 **http://localhost:8080/swagger-ui.html**

- Base URL: `/api/v1`
- 모든 인증 필요 API: `Authorization: Bearer {accessToken}` 헤더 필수
- 응답 포맷: `ApiResponse<T>` 래퍼 (`ok()` / `created()` / `fail(errorCode)`)

### HTTP 상태 코드 표준

| 코드 | 사용 케이스 |
|------|------------|
| 200 | GET · PATCH · PUT 성공 |
| 201 | POST 신규 생성 |
| 202 | 비동기 처리 수락 (STT · AI) |
| 204 | DELETE 성공 (응답 본문 없음) |
| 400 | 입력 검증 실패 · 비즈니스 규칙 위반 |
| 401 | 토큰 없음/만료/무효 |
| 403 | 권한 부족 · 이메일 미인증 |
| 404 | 리소스 없음 · **타 테넌트 접근 포함** (존재 은닉) |
| 409 | 중복 등록 · 동시 수정 충돌 |
| 410 | 인증 토큰 만료 |
| 413 | 파일 용량 초과 |
| 502 | 외부 API 장애 (LLM · DART · Gmail) |

### 주요 엔드포인트 (요약)

#### 🔐 인증 (Auth)

| Method | Endpoint | 권한 |
|--------|----------|------|
| POST | `/auth/login` | ALL |
| POST | `/auth/admin-login` | ALL |
| POST | `/auth/logout` | MEMBER+ |
| POST | `/auth/refresh` | ALL |
| GET | `/auth/email/verify?token=` | ALL |

#### 👤 사용자 · 조직 (User / Team)

| Method | Endpoint | 권한 |
|--------|----------|------|
| GET | `/users/me` | MEMBER+ |
| GET | `/users` | ADMIN · MANAGER |
| POST | `/users` | ADMIN |
| PATCH | `/users/{userId}/role` | ADMIN |
| GET / POST / PATCH / DELETE | `/teams[/{teamId}]` | ADMIN |

#### 🏭 고객사 · 담당자 (Account / Contact)

| Method | Endpoint | 권한 |
|--------|----------|------|
| GET / POST / PATCH / DELETE | `/accounts[/{accountId}]` | MEMBER+ |
| GET | `/accounts/searchable?keyword=` | MEMBER+ |
| GET | `/accounts/{id}/signals` | MEMBER+ |
| GET | `/accounts/{id}/mood` | MEMBER+ |
| GET / PATCH | `/accounts/{id}/wiki` · `/wiki/items/{itemId}` | MEMBER+ |
| GET | `/accounts/{id}/contacts` | MEMBER+ |
| POST / PATCH / DELETE | `/contacts[/{contactId}]` | MEMBER+ |
| POST | `/contacts/ocr` | MEMBER+ |

#### 💼 딜 (Deal)

| Method | Endpoint | 권한 |
|--------|----------|------|
| GET / POST / PATCH / DELETE | `/deals[/{dealId}]` | MEMBER+ |
| GET | `/deals/{id}/stage` | MEMBER+ |
| POST / DELETE | `/deals/{id}/contacts/{contactId}` | MEMBER+ |
| GET | `/accounts/{id}/deals?mineOnly=` | MEMBER+ |

#### 📅 활동 · 캘린더 (Activity / Calendar)

| Method | Endpoint | 권한 |
|--------|----------|------|
| GET / POST / PATCH / DELETE | `/activities[/{activityId}]` | MEMBER+ |
| POST | `/activities/{id}/recording` (STT 비동기) | MEMBER+ |
| GET | `/activities/{id}/ai/briefing` | MEMBER+ |
| GET | `/calendar/events` · `/calendar/events/{eventId}` | MEMBER+ |
| POST / DELETE | `/calendar/connect` · `/calendar/disconnect` | MEMBER+ |

#### 📁 파일 (File · Pre-signed URL)

| Method | Endpoint | 권한 |
|--------|----------|------|
| POST | `/files/presigned-url` · `/files/presigned-download` | MEMBER+ |
| POST | `/files/multipart/init` · `/files/multipart/complete` | MEMBER+ |

#### 📊 대시보드 (Dashboard)

| Method | Endpoint | 권한 |
|--------|----------|------|
| GET / POST / PATCH / DELETE | `/dashboards[/{dashboardId}]` | MEMBER+ |
| GET | `/dashboards/templates` | MEMBER+ |
| POST | `/dashboards/{id}/queries` → `{ traceId }` | MEMBER+ |
| GET | `/dashboards/queries/{traceId}/stream` (SSE) | MEMBER+ |
| PATCH / DELETE | `/dashboards/{id}/widgets/{widgetId}` | MEMBER+ |

#### 🤖 AI · 알림

| Method | Endpoint | 권한 |
|--------|----------|------|
| GET | `/accounts/{id}/ai/next-actions[/{suggestionId}]` | MEMBER+ |
| POST | `/ai/next-actions` | MEMBER+ |
| GET | `/notifications` | MEMBER+ |
| PATCH | `/notifications/{id}/read` · `/notifications/read-all` | MEMBER+ |
| POST | `/signals/collect` | ADMIN / SYSTEM |

전체 명세 및 미구현 항목: [`docs/requirements.md`](docs/requirements.md), [`backend/CLAUDE.md`](backend/CLAUDE.md) 참조.

---

## 📁 프로젝트 구조

```
S14P31A301/
├── backend/                    Spring Boot 4.0.5 (메인 API)
│   ├── src/main/java/com/ssafy/fint/
│   │   ├── FintApplication.java
│   │   ├── global/             공통 (ApiResponse · BaseEntity · ErrorCode · Config)
│   │   ├── auth/               인증 · JWT
│   │   ├── tenant/             테넌트 관리
│   │   ├── customer/           고객사 · 담당자
│   │   ├── deal/               영업건 · 파이프라인
│   │   ├── meeting/            미팅 (녹음 · STT 콜백)
│   │   ├── signal/             영업 시그널 (뉴스 · DART)
│   │   └── dashboard/          대시보드 · 위젯
│   ├── src/main/resources/
│   │   ├── db/migration/       Flyway V{n}__{설명}.sql
│   │   └── application-*.yml   local · dev · test
│   ├── build.gradle
│   └── Dockerfile / Dockerfile.runtime
│
├── ai/                         FastAPI (AI 전용 · stateless)
│   ├── app/
│   │   ├── main.py             App factory + lifespan
│   │   ├── core/               config · db · redis · security · errors · response
│   │   ├── clients/            llm · whisper · s3
│   │   ├── schemas/            Pydantic 모델
│   │   └── routers/            엔드포인트
│   ├── tests/
│   ├── pyproject.toml          (uv)
│   └── Dockerfile
│
├── frontend-web/               Next.js 14 (Web)
│   ├── src/
│   │   ├── app/                App Router 페이지
│   │   ├── components/         재사용 컴포넌트
│   │   ├── hooks/              Custom Hooks
│   │   ├── constants/          상수 · 토큰
│   │   ├── styles/             전역 스타일
│   │   ├── types/              TypeScript 타입
│   │   ├── test/               Vitest 셋업
│   │   └── middleware.ts       Next.js Middleware (인증 가드)
│   ├── public/
│   ├── package.json
│   └── Dockerfile
│
├── frontend-app/               Kotlin Android (minSdk 35)
│   ├── app/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── infra/                      Docker Compose · Nginx · 모니터링
│   ├── docker-compose.yml      앱 데이터 스택 (PostgreSQL + Redis)
│   ├── docker-compose.dev.yml  배포(dev) override
│   ├── nginx/
│   └── monitoring/             Prometheus · Grafana · Loki · Alertmanager
│
├── docs/                       설계 문서
│   ├── requirements.md         요구사항 명세 (REQ ID 체계)
│   ├── monitoring.md
│   └── images/
│
├── exec/                       배포 산출물
│   └── 포팅_매뉴얼.md
│
├── convention/                 팀 컨벤션
│   ├── git_convention.md
│   └── jira_convention.md
│
├── scripts/                    유틸 스크립트
│
├── .claude/                    Claude Code rules · skills · docs
├── CLAUDE.md                   Claude Code 가이드 (전체 프로젝트)
├── Jenkinsfile                 CI/CD 파이프라인
├── Makefile                    통합 make 명령
└── README.md
```

---

## 📱 주요 화면

> 실제 스크린샷 / GIF는 PR 머지 시 [`docs/images/`](docs/images/)에 추가 예정.

### 1. 메인 대시보드 (캘린더 + 사이드바)
![메인 대시보드](./docs/images/screen-main.png)

- 상단: 자연어 검색창 + 알림 + 사용자 메뉴
- 좌측 사이드바: 하루 캘린더 (오늘 일정 · F!NT 활동)
- 메인: 캘린더 뷰 (년/월/주), Deal 스테이지 필터, Next Action 드래그 등록

### 2. 고객사 상세 (Account 위키)
![고객사 상세](./docs/images/screen-account.png)

- 고객사 프로필: **날씨(Mood)** 시각화 (RAINBOW · SUNNY · CLOUDY · RAINY · THUNDER)
- 담당자(Contact) 프로필 + 직책
- 최근 시그널 (뉴스 · DART 공시) 타임라인
- 최근 딜 2개 + 더보기

### 3. 미팅 활동 (Activity · STT)
![미팅 상세](./docs/images/screen-activity.png)

- 녹음 업로드 → STT 비동기 처리 (`sttStatus` 폴링)
- Transcript + AI 요약 (핵심 논의 · 고객 니즈 · 합의 사항)
- 미팅 전 브리핑 (3가지 핵심 항목)

### 4. 자연어 대시보드
![자연어 대시보드](./docs/images/screen-dashboard.png)

- 자연어 입력 → 위젯 자동 생성 (SSE 4단계 progress)
- 위젯 드래그 배치 + 멀티 대시보드
- 템플릿: Next Action · Pipeline 리스크 · 고객 인사이트 · 매출 시뮬레이션

### 5. Deal 파이프라인
![Deal 파이프라인](./docs/images/screen-deal.png)

- 스테이지 전이 (첫 미팅 준비 → 성사/실패)
- Deal-Activity 연결
- 예상 매출 집계

---

## 💻 개발 가이드

### 코드 컨벤션

#### Java / Spring Boot

- **클래스**: PascalCase · **메서드/변수**: camelCase · **상수**: UPPER_SNAKE_CASE
- **계층 책임 분리**: Controller → Service → Repository (Controller에서 Repository 직접 호출 금지)
- **Entity ↔ DTO 변환은 Service에서 수행** (Entity를 DTO로 import하지 않음)
- **예외**: `throw new BusinessException(<Domain>ErrorCode.XXX)` 패턴. `catch (Exception)` 남발 금지
- **응답**: 모든 API는 `ApiResponse<T>` 래퍼 (`ok()` · `created()` · `fail(errorCode)`)
- **트랜잭션**: `@Transactional` 경계 명시. PostgreSQL native 트랜잭션 사용
- **로그**: `tenant_id` · `user_id` · `resource_id` 포함. 민감 정보 로그 금지
- **입력 검증**: `@Valid` 필수

도메인 패키지 구조 (컨벤션):
```
<domain>/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
│   ├── request/
│   └── response/
└── enums/
```

#### TypeScript / Next.js

- **컴포넌트**: PascalCase · **변수/함수**: camelCase · **상수**: UPPER_SNAKE_CASE
- App Router 기반. `middleware.ts`로 인증 가드
- 토큰 만료(401) → `/auth/refresh` 후 재요청
- 비동기 패턴:
  - **STT/AI**: 5초 간격 폴링 (`sttStatus`: NONE → PENDING → PROCESSING → COMPLETED)
  - **자연어 대시보드**: SSE (`@microsoft/fetch-event-source`)

### TDD (Backend)

> Red → Green → Refactor. 테스트 없는 프로덕션 코드 금지.

```bash
# Backend
cd backend && ./gradlew test

# 커버리지 리포트
./gradlew jacocoTestReport

# Frontend Web
cd frontend-web && pnpm test

# AI
cd ai && uv run pytest
```

상세 워크플로우: [`.claude/skills/tdd-workflow/SKILL.md`](.claude/skills/tdd-workflow/SKILL.md) · [`.claude/skills/tdd-frontend/SKILL.md`](.claude/skills/tdd-frontend/SKILL.md)

### Flyway 마이그레이션

- 위치: `backend/src/main/resources/db/migration/`
- 파일명: `V{번호}__{설명}.sql` (더블 언더스코어 `__` 필수)
- 예시: `V2__create_tenant_table.sql`
- Entity 변경 PR에 마이그레이션 SQL 파일 **반드시 포함**
- **머지된 마이그레이션 파일 절대 수정 금지** — 새 마이그레이션으로 변경
- `make clean && make up` 으로 깨끗한 DB에서 검증

### Git 컨벤션

#### 브랜치 네이밍

```
feat/{domain}/{지라이슈번호}/{작업내용}

예시:
feat/fe/S14P31A301-1/user-signup
feat/be/S14P31A301-2/user-signup
feat/app/S14P31A301-3/user-signup
feat/ai/S14P31A301-4/stt-pipeline
feat/infra/S14P31A301-5/jenkins
```

**domain**: `fe` · `be` · `ai` · `infra` · `app`

> Merge 완료 후 해당 브랜치 **반드시 삭제**

#### 커밋 타입

| 타입 | 의미 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 리팩토링 |
| `test` | 테스트 코드 |
| `chore` | 빌드, 패키지 매니저, 설정 |
| `docs` | 문서 |
| `!Hotfix` | 긴급 치명적 버그 수정 |

```
feat : 회원가입 DTO 클래스 추가
fix : 토큰 만료 시 리다이렉트 오류 수정
```

REQ ID는 커밋 메시지 · PR · 테스트 케이스에 명시. 예: `REQ-DEAL-02 검증: 스테이지 전이 시 이력 기록 확인`

#### MR (Merge Request)

**제목 형식**
```
[지라이슈번호] [태그]: 전체 기능 요약
예: S14P31A301-63 [feat]: 사용자 프로필 조회 기능 구현
```

**PR 템플릿**
```markdown
## ✅ PR 유형
- [ ] feat / fix / refactor / docs / test / chore / !Hotfix

## 🚀 작업 내용
- 예) 로그인 응답에 문제가 생겨서 분기 처리 했습니다

## 💬 기타 사항

## 🎯 Resolve
- closes S14P31A301-()
```

상세: [convention/git_convention.md](convention/git_convention.md) · [convention/jira_convention.md](convention/jira_convention.md)

### 코드 리뷰 체크리스트

- [ ] 코드 컨벤션 준수
- [ ] 테스트 코드 작성 (Red → Green → Refactor)
- [ ] `tenant_id` 필터 누락 여부 확인
- [ ] 예외/에러 처리 (`BusinessException` 패턴)
- [ ] 보안 취약점 확인 (로그에 민감 정보 노출 X)
- [ ] 성능 영향 (N+1, 트랜잭션 범위)
- [ ] API 명세/Swagger 문서화

---

## 🚢 배포

### 아키텍처

```
Internet
    │
    ▼
┌──────────────────────────────────────────────────────────────┐
│                      Lightsail XL                            │
│           (4 vCPU / 16GB / 320GB SSD · Single Node)          │
│           Docker Compose 기반 단일 배포                         │
│                                                              │
│   ┌────────┐                                                 │
│   │ Nginx  │ :80 (→ HTTPS 301), :443 (TLS)                  │
│   └───┬────┘                                                 │
│       │                                                      │
│       ├── /         ────► frontend-web (:3000)               │
│       ├── /api/v1   ────► backend Spring (:8080)             │
│       └── (내부망)   ────► ai FastAPI (:8000)                  │
│                                                              │
│   ┌──────────┐  ┌──────────────┐  ┌────────────────────┐     │
│   │  Redis   │  │ PostgreSQL   │  │  Prometheus+Loki   │     │
│   │  :6379   │  │ +pgvector    │  │  +Grafana          │     │
│   │          │  │ :5432        │  │ +Alertmanager      │     │
│   └──────────┘  └──────────────┘  └────────────────────┘     │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼  (외부)
                       ┌─────────────┐
                       │   AWS S3    │  Pre-signed URL 업로드
                       └─────────────┘
```

### CI/CD

```
GitLab Push (dev 브랜치)
    ↓
Jenkins Webhook
    ↓
빌드 (Gradle · pnpm · uv)
    ↓
Docker Image 빌드
    ↓
Lightsail SSH → docker compose pull && up -d
    ↓
헬스체크 (Actuator) · Slack/Mattermost 알림
```

상세는 루트 [`Jenkinsfile`](Jenkinsfile) 참조. 배포 매뉴얼은 [`exec/포팅_매뉴얼.md`](exec/포팅_매뉴얼.md).

### 환경 변수 (요약)

| 변수 | 사용처 | 설명 |
|------|--------|------|
| `POSTGRES_USER/PASSWORD/DB/PORT` | Spring · FastAPI | DB 연결 |
| `REDIS_HOST/PORT/PASSWORD` | Spring · FastAPI | 캐시/큐 |
| `JWT_SECRET` | Spring · FastAPI | JWT 서명/검증 |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | FastAPI | LLM 호출 |
| `S3_BUCKET/REGION/AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY` | Spring · FastAPI | S3 |
| `DART_API_KEY` | Spring | 공시 수집 |
| `GOOGLE_CLIENT_ID/SECRET` | Spring | Calendar OAuth |

`.env.local.example`, `.env.dev.example` 템플릿 참조. `.env*`는 절대 커밋 금지.

---

## 📊 프로젝트 통계

> 측정 시점 기준 (`README_NEW.md` 작성 시점). 정확한 수치는 GitLab / Jira에서 확인.

| 항목 | 수치 |
|------|------|
| 개발 기간 | 2026.04 ~ 진행 중 |
| 팀원 수 | 6명 |
| 총 커밋 수 | 935+ |
| 도메인 패키지 | auth · tenant · customer · deal · meeting · signal · dashboard |
| 주요 ENUM | role · mood · activity_type · stt_status · widget_type 외 |

---

## 👥 팀 소개

| 이름 | 담당 | 비고 |
|------|------|------|
| 이세원 | 팀장 · PM · BE · FE | |
| 채지원 | BE · FE | |
| 류수정 | BE · FE | |
| 김민재 | BE · FE | |
| 정은지 | BE · Infra | |
| 정대철 | BE · Infra | |

> 세부 역할(FE / BE / INFRA / AI)은 스프린트 단위로 조정.

---

## 📚 참고 자료

### 프로젝트 문서

- [요구사항 명세서 (REQ ID)](docs/requirements.md)
- [백엔드 README](backend/README.md)
- [프론트엔드 웹 README](frontend-web/README.md)
- [AI 서비스 README](ai/README.md)
- [인프라 README](infra/README.md)
- [모니터링 가이드](docs/monitoring.md)
- [포팅 매뉴얼](exec/포팅_매뉴얼.md)
- [Claude Code 가이드 (전체 프로젝트)](CLAUDE.md)
- [Claude Code 가이드 (백엔드 상세)](backend/CLAUDE.md)
- [Claude Code 가이드 (프론트엔드 상세)](frontend-web/CLAUDE.md)

### 컨벤션

- [Git Convention](convention/git_convention.md)
- [Jira Convention](convention/jira_convention.md)

### 공식 문서

- [Spring Boot 4.0 Documentation](https://spring.io/projects/spring-boot)
- [Next.js 14 Documentation](https://nextjs.org/docs)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [PostgreSQL 16 Documentation](https://www.postgresql.org/docs/16/)
- [pgvector](https://github.com/pgvector/pgvector)
- [Redis Documentation](https://redis.io/docs/)
- [DART OpenAPI](https://opendart.fss.or.kr/)
- [Google Calendar API](https://developers.google.com/calendar)

---

**F!NT** — *Flow + Hint* — 흐름을 읽고, 다음 행동을 제시한다.

[⬆ 맨 위로 이동](#-fnt-핀트)
