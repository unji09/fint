# F!NT — 지능형 영업관리를 위한 인공지능 B2B CRM

> *"기록하는 CRM이 아니라, 행동을 만들어내는 CRM"*

뉴스·공시·미팅 이력을 자동 수집·구조화하고, 영업사원에게 다음 행동(Next Best Action)을 제시하는 B2B CRM.

**SSAFY 14기 자율 PJT** · Team A301 · 철철이형팬클럽 · 2026.04 ~

> 본 문서는 확정 사양이 아닌 **작업 기준선**. 수집 대상·데이터 구조·대시보드 구성은 변경 여지 있음.

---

## 핵심 기능 (3 Layer)

- **Input** — 명함 OCR · 미팅 녹음 STT · Gmail/캘린더/DART/뉴스 자동 수집
- **Knowledge** — 고객 위키 자동 생성 · 미팅 전 자동 브리핑
- **Action** — 자연어 커스텀 대시보드 · Challenger 기반 NBA 추천

**핵심 원칙.** HMI — AI는 추천, 최종 판단과 책임은 사람.

---

## 기술 스택

| 영역 | 스택 |
|------|------|
| Web | Next.js (React) |
| App | Kotlin 네이티브 (Android, minSdk 35) |
| Backend | Spring Boot (Java) · 메인 API |
| AI | FastAPI (Python) · stateless |
| Data | PostgreSQL (메인) · Redis · AWS S3 |
| Infra | AWS Lightsail XL · Docker Compose · Nginx |
| CI/CD | GitLab → Jenkins → Docker |
| Monitoring | Prometheus · Loki · Grafana |

---

## 아키텍처
- [Git Convention (Notion)](https://www.notion.so/Git-Convention-3324479f510880f6b3b8f812ccf676e6)
![System Architecture](docs/images/architecture.png)

상세: [Draw.io](https://drive.google.com/file/d/16TekfqxYtelc9bR-hm7cTv0CNRSkxOSR/view?usp=drive_link)

---

## 프로젝트 구조

```
fint/
├── backend/           Spring Boot (메인 API)
├── ai/                FastAPI (AI 전용)
├── frontend-web/      Next.js
├── frontend-app/      Kotlin 네이티브 (Android)
├── infra/             Docker Compose · Nginx · 모니터링
├── docs/              설계 문서
├── .claude/           Claude Code rules · skills
└── CLAUDE.md          Claude Code 가이드
```

---

## 시작하기

### 전제 조건
- Docker Desktop (Windows/Mac) 또는 Docker Engine + Compose plugin (Linux)
- Git
- make (Windows: `choco install make` 또는 `winget install ezwinports.make`)
- JDK 21은 Gradle이 자동 다운로드 (수동 설치 불필요)

### 퀵스타트

```bash
git clone <repo-url>
cd S14P31A301
git checkout dev

make doctor    # 환경 검사 + .env 자동 생성
make up        # PostgreSQL + Redis 기동
make backend   # Spring Boot 기동 (별도 터미널)
```

기동 확인:
- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### 주요 make 명령

| 명령 | 설명 |
|------|------|
| `make doctor` | 환경 사전 검사 (Docker, 포트, JDK, .env) |
| `make up` | PostgreSQL + Redis 기동 |
| `make down` | PostgreSQL + Redis 정지 |
| `make backend` | Spring Boot 기동 (local 프로파일) |
| `make logs` | 인프라 컨테이너 로그 |
| `make clean` | DB 볼륨 포함 전체 초기화 |
| `make status` | 컨테이너 상태 + Spring 헬스체크 |
| `make monitoring-up` | Prometheus + Grafana 기동 |

### (선택) 모니터링 스택

```bash
make monitoring-up
```

접속: Grafana http://localhost:3000 / Prometheus http://localhost:9090 / Alertmanager http://localhost:9093

### 수동 설정 (make 없이)

```bash
# 1. 데이터 스택 기동
cd infra && cp .env.example .env && docker compose up -d

# 2. 백엔드 기동 (별도 터미널)
cd backend && cp .env.example .env && ./gradlew bootRun --args='--spring.profiles.active=local'
```

### 서비스별 상세 문서

| 서비스 | 역할 | README |
| --- | --- | --- |
| `infra/` | Docker Compose 데이터 스택 + 모니터링 | [infra/README.md](infra/README.md) |
| `backend/` | Spring Boot (메인 API) | [backend/README.md](backend/README.md) |
| `ai/` | FastAPI (AI 전용, 스켈레톤) | [ai/README.md](ai/README.md) |
| `frontend-web/` | Next.js (웹) | [frontend-web/README.md](frontend-web/README.md) |
| `frontend-app/` | Kotlin 네이티브 (Android) | [frontend-app/README.md](frontend-app/README.md) |

---

## 문서

### 설계

- [요구사항 명세서](docs/requirements.md)
- [3 Layer 기능 구조](docs/3-layer.md)
- [ERD (PostgreSQL)](docs/erd.md)
- [API 명세](docs/api.md)
- [AI 파이프라인](docs/ai-pipeline.md)
- [영업 시그널 정의](docs/sales-signals.md) *(검토 중)*
- [Playbook (Challenger · SPIN)](docs/playbook.md) *(검토 중)*
- [대시보드 후보](docs/dashboard-candidates.md) *(검토 중)*

### 개발 컨벤션

- [Git Convention (Notion)](https://www.notion.so/Git-Convention-3324479f510880f6b3b8f812ccf676e6)
- [Jira Convention (Notion)](https://www.notion.so/Jira-Convention-3324479f5108805db009fe8e95e36121)

### 운영

- [인프라 · 배포 · 모니터링](docs/infra.md)
- [모니터링 가이드](docs/monitoring.md)
- [Claude Code 가이드](CLAUDE.md)

---

## 팀

| 이름 | 담당 |
|------|------|
| 이세원 | 팀장 · PM · BE · FE|
| 채지원 | BE · FE    |
| 류수정 | BE · FE    |
| 김민재 | BE · FE    |
| 정은지 | BE · Infra |
| 정대철 | BE · Infra |

> 세부 역할(FE / BE / INFRA / AI)은 스프린트 0 확정 후 업데이트.

---

**F!NT** · *Flow + Hint*