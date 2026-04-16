---
paths: src/main/java/**
---

# 계층 책임 분리

## Controller/API 계층
- 요청 파싱/검증, 응답 구성, 인증 주체 전달만 담당
- Repository 직접 호출 금지
- 도메인 규칙 검증 로직(소유권/상태 전이/정책 판단) 구현 금지
- 입력 값 검증 필수

## Service 계층
- 비즈니스 규칙, 트랜잭션 경계, 도메인 조합 담당
- 컨트롤러에 비즈니스 로직을 남기지 않음
- 트랜잭션 범위 적절성 확인

## Repository 계층
- 조회/저장만 담당
- 타입 변환, 도메인 정책 로직 구현 금지

## DTO/ReadModel/Response 네이밍
- 조회 전용: `*ReadModel`, `*Summary`, `*ListItem`
- API 출력: `*Response`
- API 입력: `*Request`
- `*DTO` 범용 네이밍 남용 금지
- Response가 Document 직접 참조 금지
- 매핑은 Service/Mapper에서 수행. Document 를 Response 까지 그대로 전달 금지

## 공통 응답 객체 위치
- `ApiResponse`, `PaginationResponse`류는 도메인 하위 패키지에 두지 않음
- `global/common` 또는 `global/dto` 전역 레이어로 이동해 재사용
- `from(Page)` 팩토리 메서드는 `public static`으로 제공
- 공통 응답 생성 시 현재 시간 직접 호출 대신 `Clock` 주입 기반 구조 우선 (테스트 용이성)
