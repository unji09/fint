# F!NT - Claude Code 가이드

## 응답 규칙
- **문어체 사용**: 반말/존댓말이 아닌 문어체로 답변한다.
- **무조건적 긍정/찬성 금지**: 문제가 있으면 명확히 지적한다.
- **피드백 방식**: 현재 구현 상태 → 문제점 → 대안 비교 → 권장안 제시.
- **확실하지 않은 정보 제시 금지**: 모르면 모른다고 한다. 꾸며내지 않는다.

## 필수 규칙
- **TDD**: Red → Green → Refactor. 테스트 없는 프로덕션 코드 금지.
- **멀티에이전트**: 탐색/검증은 subagent로. 메인 context 오염 방지.
- **Git 조작 금지**: Claude는 git 명령어를 실행하지 않음. 커밋/푸시/브랜치/PR은 사용자가 직접 수행.

## 규칙 충돌 시 우선순위
1. **런타임 안정성 / 데이터 정합성** (특히 `tenant_id` 멀티테넌트 격리)
2. 계층 책임 분리
3. 타입/컨벤션 일관성
4. 네이밍 / 가독성

## 프로젝트 특화 원칙

### 멀티테넌트 (최우선)
- 모든 MongoDB 쿼리는 `tenant_id` 필터를 강제한다.
- Controller에서 `tenant_id`를 직접 읽지 않고, `SecurityContext` / Interceptor를 거쳐 주입받는다.
- 테스트에서도 `tenant_id` 누락 케이스(다른 테넌트 데이터 노출)를 반드시 검증한다.

### Spring ↔ FastAPI 경계
- FastAPI는 **stateless**. Spring이 호출할 때 필요한 데이터를 payload로 전부 전달한다.
- FastAPI는 **외부 노출 없음**. Nginx → Spring 경로만 외부 진입.
- Spring → FastAPI 호출은 타임아웃 · 재시도 · circuit breaker 정책을 명시한다.

### 비용 / 성능
- LLM 응답은 Redis에 캐싱한다. 캐시 키 설계 시 `tenant_id` 포함.
- 파일 업로드는 Pre-signed URL 방식. 서버 경유 업로드 금지.

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
- **작업 시작 전**: 반드시 `dev` 브랜치로 이동 → `dev` 최신화(`pull`) → `dev`에서 `feat/<domain>/#<issue>/<task>` 브랜치 생성 후 작업
- PR에 Summary + Test plan 필수
- 하나의 PR에 하나의 기능/목적/커밋 단위만
- `dev`에 직접 push 금지 — 항상 PR을 통해 머지
- 상세 Git / Jira 컨벤션은 팀 Notion 참조

## 기본 컨벤션 요약
- 계층 책임 분리 (Controller → Service → Repository)
- Document는 DTO를 import하지 않음. 변환은 Service에서 수행
- 예외는 구체 타입으로 (`BadRequestException`, `NotFoundException` 등). `catch (Exception)` 남발 금지
- 로그에 식별자(`tenant_id`, `user_id`, `resource_id`) 포함, 민감 정보 로그 금지
- DTO / Response 분리. Document 직접 노출 금지
- 입력 값 검증 필수 (`@Valid`)
- 트랜잭션 범위 적절성 확인 (MongoDB는 replica set에서만 multi-document 트랜잭션 가능)

## 리팩토링 작업 절차
- 변경 전/후 요약(Before/After) 필수 제공
- 커밋 시점에 커밋 메시지를 사용자에게 제시 (자동 커밋 금지)
- 기술 선택의 면접 설명 가능성 고려
- 오버엔지니어링 경계: 매 선택마다 "이게 진짜 필요한가?" 자문

> 상세 규칙은 `.claude/rules/`에 경로별 조건부로 정의됨.
> 개발 방법론: `.claude/skills/tdd-workflow/`, `.claude/skills/multi-agent-orchestration/`
> 리팩토링 상세: `.claude/skills/refactoring-workflow/`
> 설계 문서는 `.claude/docs/README.md` 참조.
