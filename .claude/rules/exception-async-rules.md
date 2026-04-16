---
paths: src/main/java/**
---

# 예외/로그 컨벤션

- 광범위 `catch (Exception)`은 불가피한 경계에서만 허용. 구체 예외로 분리
- `log.error(e.getMessage())` 금지. 식별자 + 예외 객체를 함께 기록
- 외부 API 에러 본문 전체(raw body) 로그 금지. 마스킹/길이/코드 중심 기록
- 비밀값(token, secret, password, 전체 payload) 로그 금지
- `InterruptedException` 처리 시 interrupt restore 필수
- 리소스는 `try-with-resources` 사용
- 필요 시 트랜잭션/재시도 정책 명시

# 비동기/동시성 컨벤션

- `@Async`는 `public` 메서드 + Spring proxy 경로에서만 사용
- self-invocation 비동기 무효화 방지: 별도 빈으로 분리
- `@Async` 호출 경로에서 실제 비동기 동작 확인
- WebSocket/이벤트 fan-out 경로는 bounded executor + backpressure 정책 사용
- 멀티스레드/공유 상태 동시성 안전성 고려
- 인터럽트/종료 신호 무시 없음
