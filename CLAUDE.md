# F!NT - Claude Code 가이드

## 서비스 개요
B2B 영업 CRM. 뉴스/DART 데이터를 자동 수집해 영업 시그널로 변환하고, AI가 다음 행동을 추천한다.

## 타깃 규모
- B2B 5개사 × 100명 = 500명
- CCU 150명, p95 응답시간 500ms 이하 목표

## 기술 스택

### Frontend
- **Web**: Next.js (React)
- **Android**: Kotlin 네이티브 (`minSdkVersion 35` = Android 15 / OneUI 7 이상)
- 두 플랫폼 동일 API 사용. 기능/권한 차이 금지.

### Backend
- **Spring Boot 4.0.5 (Java 21)**: 메인 API. 비즈니스 로직, 인증(JWT), CRUD, 파일 메타 관리, 알림, 스케줄링, 외부 데이터 수집(DART/뉴스).
- **FastAPI (Python)**: AI 전용 stateless 서비스. LLM / RAG / 스코어링 / STT / 화자 분리 처리.
- Spring → FastAPI **일방향 호출**. FastAPI는 외부 노출 없음.

### Data
- **PostgreSQL**: 마스터 DB (고객/딜/미팅/스코어/위키/감사로그 등). Spring 소유. `tenant_id`로 멀티테넌트 분리.
  - 정형 필드는 **컬럼**, 가변 부가정보는 **JSONB** 하이브리드 전략.
  - **쓰기는 Spring만**. FastAPI는 **읽기 전용**으로 접근 가능 (단, `tenant_id` 필터 필수).
- **Redis**: 세션 / 캐시 / 비동기 큐(Stream). **Spring + FastAPI 양방향** 사용 (소유는 Spring).
- **AWS S3**: 파일 원본 (녹음/문서/이미지). Pre-signed URL 방식 업로드.

### Infra
- AWS Lightsail XL (4 vCPU / 16GB RAM / 320GB SSD) 단일 인스턴스
- Docker Compose로 모든 컨테이너 관리
- Nginx Reverse Proxy (단일 진입점)
- 환경 분리: `local` / `dev` / `prod`

### CI/CD & 모니터링
- GitLab → Jenkins → Docker Image → 배포
- Prometheus + Loki + Grafana

## 아키텍처 핵심 원칙
1. **FastAPI는 stateless**. 필요한 데이터는 Spring이 payload로 전달하거나, FastAPI가 PostgreSQL에서 직접 읽는다.
2. **Nginx → Spring 단일 진입**. FastAPI는 내부망 전용.
3. **파일 업로드는 Pre-signed URL** (서버 경유 업로드 금지).
   - URL 발급: **Spring 단일 주체**.
   - 실제 업로드: 클라이언트 → S3 직통.
   - FastAPI는 S3에서 `boto3`로 직접 읽기 (pre-signed 불필요).
4. **`tenant_id` 필터링을 Interceptor로 강제**. Spring / FastAPI 양쪽 모두 적용.
5. **LLM 응답은 Redis 캐싱**. 캐시 키에 `tenant_id` 포함.
6. **PostgreSQL 쓰기는 Spring만, 읽기는 Spring + FastAPI**.
7. **Spring → FastAPI 호출**은 타임아웃 · 재시도 · circuit breaker 정책을 명시한다.

## 외부 서비스 호출 주체
- **Spring**: DART API, 뉴스 API/크롤링, FCM
- **FastAPI**: LLM API (OpenAI / Claude), STT API (Whisper)

## 기획서 관련 주의
- 최초 기획서(영업 시그널 정의, 3 Layer 구조, 대시보드 후보 등)는 **러프한 초안**.
- 수집 대상, 데이터 구조화 항목, 활용 레이어 요소는 **언제든 변경 가능**.
- 기획서 내용을 **확정 사항으로 단정짓지 말고**, 변경 여지를 열어둘 것.

## 요구사항 참조 규칙
- 새 기능 / 엔드포인트 / 도메인 모델 구현 전, `docs/requirements.md`에서 해당 도메인 섹션(2.1~2.15)을 먼저 확인한다.
- **REQ ID는 커밋 메시지 · PR · 테스트 케이스에 명시**한다. 예: `REQ-DEAL-02 검증: 스테이지 전이 시 이력 기록 확인`.
- 코드 구현과 명세가 충돌하면 **명세가 우선**. 불명확하면 임의로 확장하지 말고 사용자에게 확인한다.
- 명세에 없는 기능을 추가하려면 먼저 `docs/requirements.md` 업데이트를 제안한다.
- `3.4 미결 사항(Open Questions)`에 속한 항목은 **가정 없이 사용자 확인 후** 진행한다.
- 핵심 용어(Tenant / Account / Contact / Deal / Activity / Wiki / Signal)와 Deal 스테이지 코드는 명세서(1.5, 1.6)의 정의를 그대로 사용한다.

## 응답 규칙
- **문어체 사용**: 반말/존댓말이 아닌 문어체.
- **현업 기준 실질 답변**: 과도한 격식 배제, 핵심 위주로 간결하게.
- **무조건적 긍정/찬성 금지**: 문제가 있으면 명확히 지적.
- **피드백 방식**: 현재 구현 상태 → 문제점 → 대안 비교 → 권장안.
- **할루시네이션 금지**: 근거 없는 추측·생성 금지. 모르면 모른다고 한다.
- **버전/스펙 정보**: 공식 문서 확인 후 답변.

## 필수 규칙
- **TDD**: Red → Green → Refactor. 테스트 없는 프로덕션 코드 금지.
- **멀티에이전트**: 탐색/검증은 subagent로. 메인 context 오염 방지.
- **Git 조작 범위**:
  - **허용 (Claude 실행 가능)**: 브랜치 전환/생성 (`checkout`, `switch`, `checkout -b`), 최신화 (`pull`, `fetch`), 조회 (`status`, `log`, `diff`, `branch`, `remote -v`)
  - **금지 (사용자 직접 수행)**: 변경 확정 (`add`, `commit`), 원격 조작 (`push`, PR 생성), 병합/재작성 (`merge`, `rebase`, `cherry-pick`, `tag`)
  - **파괴적 작업은 사용자 명시 승인 필요**: `reset --hard`, `checkout -- <file>`, `branch -D`, `push --force`, `stash drop/clear`
  - 원칙: 작업 공간 준비는 Claude, 변경 확정과 원격 반영은 사용자

## 규칙 충돌 시 우선순위
1. **런타임 안정성 / 데이터 정합성** (특히 `tenant_id` 멀티테넌트 격리)
2. 계층 책임 분리
3. 타입 / 컨벤션 일관성
4. 네이밍 / 가독성

## 멀티테넌트 세부 규칙 (최우선)
- 모든 PostgreSQL 쿼리(Spring · FastAPI 공통)는 `tenant_id` 필터를 강제한다.
- Controller / FastAPI Endpoint에서 `tenant_id`를 직접 읽지 않고, `SecurityContext` / Interceptor / Dependency를 거쳐 주입받는다.
- Spring → FastAPI 호출 시 `tenant_id`는 JWT 또는 내부 헤더로 전달하며, FastAPI 쪽에서도 재검증한다.
- 테스트에서 `tenant_id` 누락 케이스(다른 테넌트 데이터 노출)를 반드시 검증한다.

## 비용 / 성능
- LLM 응답은 Redis에 캐싱. 캐시 키에 `tenant_id` 포함.
- 파일 업로드는 Pre-signed URL. 서버 경유 업로드 금지.

## 테스트 명령어
```bash
# Backend
cd backend
./gradlew test
./gradlew jacocoTestReport

# Frontend Web
cd frontend-web
pnpm test

# Frontend App
cd frontend-app
pnpm test

# AI
cd ai
uv run pytest
```

## Git 브랜치 규칙
- **작업 시작 전**: 반드시 `dev` 브랜치로 이동 → `dev` 최신화(`pull`) → `dev`에서 `feat/<domain>/#<issue>/<task>` 브랜치 생성 후 작업.
- PR에 Summary + Test plan 필수.
- 하나의 PR에 하나의 기능/목적/커밋 단위만.
- `dev`에 직접 push 금지 — 항상 PR을 통해 머지.
- 상세 Git / Jira 컨벤션은 팀 Notion 참조.

## 기본 컨벤션 요약
- 계층 책임 분리 (Controller → Service → Repository).
- Entity는 DTO를 import하지 않음. 변환은 Service에서 수행.
- 예외는 `BusinessException(ErrorCode)` 패턴 사용. `catch (Exception)` 남발 금지.
- 로그에 식별자(`tenant_id`, `user_id`, `resource_id`) 포함, 민감 정보 로그 금지.
- DTO / Response 분리. Entity 직접 노출 금지.
- 입력 값 검증 필수 (`@Valid`).
- 트랜잭션 범위 적절성 확인 (PostgreSQL은 native 트랜잭션 지원. `@Transactional` 경계 명시).

## 리팩토링 작업 절차
- 변경 전/후 요약(Before/After) 필수 제공.
- 커밋 시점에 커밋 메시지를 사용자에게 제시 (자동 커밋 금지).
- 기술 선택의 면접 설명 가능성 고려.
- 오버엔지니어링 경계: 매 선택마다 "이게 진짜 필요한가?" 자문.

> 상세 규칙은 `.claude/rules/`에 경로별 조건부로 정의됨.
> 개발 방법론: `.claude/skills/tdd-workflow/`, `.claude/skills/multi-agent-orchestration/`
> 리팩토링 상세: `.claude/skills/refactoring-workflow/`
> 설계 문서는 `.claude/docs/README.md` 참조.
