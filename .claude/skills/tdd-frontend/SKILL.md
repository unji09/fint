---
name: tdd-frontend
description: 프론트엔드 TDD 워크플로우 가이드 (프레임워크 비종속)
---

# 프론트엔드 TDD 워크플로우

TDD 기본 사이클(RED → GREEN → REFACTOR)은 tdd-workflow skill 참조.

## 테스트 계층

| 계층 | 대상 | 목적 |
|------|------|------|
| Unit | 훅, 유틸, 서비스 함수 | 순수 로직, 상태 변경, API 호출 |
| Component/Integration | 컴포넌트 렌더링 | UI 렌더링, 사용자 인터랙션 |

## 필수 테스트 케이스

- 초기 렌더링 상태
- 로딩 상태
- 에러 상태
- 빈 데이터 상태
- 사용자 인터랙션 (클릭, 입력, 폼 제출)
- 접근성 (role, aria-label)

## 테스트 파일 위치
소스 파일 옆에 .test.ts(x) 배치
src/hooks/useAuth.ts
src/hooks/useAuth.test.ts
src/components/LoginButton.tsx
src/components/LoginButton.test.tsx
## 테스트 구조

```typescript
describe('테스트 대상', () => {
  beforeEach(() => { /* mock 초기화 */ });

  it('한국어로 기대 동작 기술', () => {
    // Given - 준비
    // When - 실행
    // Then - 검증
  });
});
```
