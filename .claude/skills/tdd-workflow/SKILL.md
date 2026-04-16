---
name: tdd-workflow
description: TDD Red-Green-Refactor 상세 워크플로우 가이드 (Backend)
---

# TDD 워크플로우

## 원칙

1. 테스트가 설계를 이끈다: 테스트를 먼저 작성하면 인터페이스 설계가 자연스럽게 결정됨
2. 작은 단위로 반복: 한 번에 하나의 테스트만 추가하고, 통과시킨 후 다음으로
3. 100% 커버리지: 모든 분기(if/else), 예외, 경계값을 테스트

## RED → GREEN → REFACTOR

### RED (실패하는 테스트 작성)
- 구현하려는 기능의 **기대 동작**을 테스트로 표현
- 테스트 실행 → 실패 확인 (컴파일 에러 포함)
- 한 번에 하나의 테스트만 추가

### GREEN (최소한의 코드로 통과)
- 테스트를 통과시키는 **가장 간단한 코드** 작성
- 과도한 설계 금지 — 테스트가 요구하는 것만 구현
- 테스트 실행 → 통과 확인

### REFACTOR (코드 개선)
- 중복 제거, 네이밍 개선, 패턴 통일
- 리팩토링 후 **반드시 테스트 재실행** → 통과 확인
- 테스트 코드도 리팩토링 대상

## Backend (Spring Boot + JUnit 5)

### 테스트 계층별 가이드

| 계층 | 어노테이션 | 목적 |
|------|-----------|------|
| Service (Unit) | `@ExtendWith(MockitoExtension.class)` | 비즈니스 로직 검증 |
| Controller (Integration) | `@WebMvcTest` | HTTP 요청/응답, 보안 검증 |
| Repository (Integration) | `@DataJpaTest` | 쿼리, 영속성 검증 |
| 외부 API | WireMock | 외부 서비스 모킹 |

### 테스트 구조: Given-When-Then

```java
@Test
@DisplayName("한국어로 테스트 의도를 명확히 기술")
void methodName_condition_expectedResult() {
    // Given - 테스트 데이터 준비
    // When - 테스트 대상 실행
    // Then - 결과 검증 (assertThat, verify)
}
```

### 필수 테스트 케이스
- Happy path (정상 동작)
- Error path (예외, 에러 응답)
- Edge cases (null, 빈 문자열, 0, 최대값)
- 권한/인증 케이스 (401, 403)
- 트랜잭션/동시성 (해당 시)

### 테스트 파일 위치
src/main/java/com/example/service/FooService.java
src/test/java/com/example/service/FooServiceTest.java
### 커버리지 확인

```bash
./gradlew jacocoTestReport
```

커버리지 부족 시: 미커버 라인 확인 → 테스트 추가 → 재확인 → 100% 도달까지 반복
