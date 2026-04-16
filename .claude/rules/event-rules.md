---
paths: src/main/java/**/listener/**, src/main/java/**/event/**
---

# 이벤트 리스너 안정성

## @TransactionalEventListener(AFTER_COMMIT)
- 이벤트 payload에 Entity 자체를 담지 않음
- 최소 식별자(`paymentId`, `orderId`) 또는 직렬화 가능한 스냅샷 payload만 전달
- 리스너에서 엔티티 재조회 시 fetch join/projection으로 명시 조회

## 실패 처리
- 예외 catch 후 로그만 남기고 종료하는 구조 금지
- 최소 하나 이상 적용:
  - 재시도 정책
  - Outbox/Queue 기반 비동기 전달
  - 실패 메트릭/알람

## 리스너 두께
- 리스너는 오케스트레이션만 담당
- payload 매핑은 `*EventMapper`로 분리
