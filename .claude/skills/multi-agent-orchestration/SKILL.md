---
name: multi-agent-orchestration
description: 멀티에이전트 병렬 개발 프로세스 가이드
---

# 멀티에이전트 오케스트레이션

Claude Code의 Task 도구를 활용하여 독립적인 작업을 병렬로 수행한다.

## 에이전트 유형

| 에이전트 | 용도 | 예시 |
|---------|------|------|
| Task(Explore, quick) | 특정 파일/함수 찾기 | 컨트롤러 위치 확인 |
| Task(Explore, very thorough) | 전체 흐름 분석 | 버그 원인 추적 |
| Task(Plan) | 구현 전략 수립 | 변경 계획, TDD 케이스 목록 |
| Task(Bash) | 테스트/빌드 실행 | ./gradlew test 등 |

## 4단계 개발 프로세스

### Phase 1: 분석 (Analyze) — 병렬 에이전트
동시 실행:
├── Task(Explore): 백엔드 관련 코드 탐색
├── Task(Explore): 프론트엔드 관련 코드 탐색
└── Task(Explore): 기존 테스트 패턴 분석
### Phase 2: 계획 (Plan)

분석 결과를 종합하여 구현 계획 수립:
- 변경할 파일 목록
- TDD 단계별 작업 (어떤 테스트를 먼저 작성할지)
- TodoWrite로 단계별 작업 목록 생성

### Phase 3: 구현 (Implement) — TDD 사이클

**병렬 가능**: 백엔드/프론트엔드 독립 작업
[Agent 1: Backend]  → Service 테스트 → Service 구현 → Controller 테스트 → Controller 구현
[Agent 2: Frontend] → 단위 테스트 → 구현 → 통합 테스트 → 구현
**순차 필수**: DB 스키마 → 엔티티 → 서비스 → 컨트롤러

### Phase 4: 검증 (Verify) — 병렬 에이전트
동시 실행:
├── Task(Bash): Backend 테스트 (./gradlew test)
├── Task(Bash): Frontend 테스트 (프론트엔드 있는 경우)
└── Task(Bash): 빌드 검증

모두 통과해야 작업 완료로 판단.

## 핵심 규칙

1. 독립 작업은 항상 병렬로: 탐색, 검증 단계에서 에이전트 동시 실행
2. 의존 작업은 순차적으로: TDD 사이클 내 RED→GREEN→REFACTOR 순서 준수
3. 검증은 반드시 수행: 구현 완료 후 전체 테스트 + 빌드 병렬 확인
4. 결과 종합 판단: 병렬 에이전트 결과를 모두 수집 후 다음 단계 진행
5. 탐색/조사는 subagent로: 메인 context에서 파일 수십 개 읽으면 context 폭발
6. "subagent로 해"를 명시적으로 프롬프트에 포함해야 함. 안 붙이면 메인 context에서 직접 읽음
