# F!NT Backend (Spring Boot)

메인 API 서버. 비즈니스 로직 · 인증 · CRUD · 파일 메타 · 스케줄링 · 외부 데이터 수집(DART/뉴스) 담당.

AI 관련 처리는 내부망의 FastAPI 로 위임 (`ai.server-url`).

---

## 기술 스택

- Java 21, Gradle 8.14
- Spring Boot 4.0.5
- Spring Data JPA (PostgreSQL) · Spring Data Redis
- Spring Security + JWT (`jjwt 0.12.6`)
- Springdoc OpenAPI 3.0.3
- AWS SDK for Java v2 (S3)

---

## 빠른 시작

```bash
# 1. 환경변수 준비
cp .env.example .env   # 값 채우기

# 2. 로컬 의존성 기동 (PostgreSQL + Redis)
#    프로젝트 루트의 infra/ 디렉토리에 docker-compose 가 있다면 그걸 쓰고,
#    없다면 임시로:
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_USER=fint -e POSTGRES_PASSWORD=fint -e POSTGRES_DB=fint_local \
  postgres:16
docker run -d --name redis -p 6379:6379 redis:7

# 3. 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

Swagger: http://localhost:8080/swagger-ui.html

---

## 프로파일

| Profile | 용도 | DB | DDL |
|---------|------|----|----|
| `local` | 개발자 로컬 | 로컬 도커 PostgreSQL/Redis | `update` |
| `dev` | EC2 배포 서버 | Lightsail PostgreSQL/Redis | `validate` |

모든 값은 `.env` 또는 환경변수로 주입. `application-*.yml` 에는 기본값 + `${VAR:default}` 패턴만 둔다.

---

## 패키지 구조

```
com.ssafy.fint/
├── FintApplication.java
├── global/                     # 전역 공통
│   ├── ApiResponse.java
│   ├── common/
│   │   ├── constant/            # ApiPath, ErrorMessage
│   │   └── entity/              # BaseEntity (멀티테넌트 + 타임스탬프)
│   ├── config/                  # JpaConfig, OpenApiConfig, SecurityConfig, RestTemplateConfig
│   └── exception/               # ErrorCode + BusinessException + GlobalExceptionHandler
│
├── auth/                        # (추후) 인증 · JWT 발급 · 재발급
├── tenant/                      # (추후) 테넌트 관리
├── customer/                    # (추후) 고객사
├── deal/                        # (추후) 딜
├── meeting/                     # (추후) 미팅 (녹음 + STT)
├── signal/                      # (추후) 영업 시그널 (뉴스 · DART)
├── dashboard/                   # (추후) 대시보드
│
└── infra/                       # (추후) 인프라 연동
    ├── postgres/
    ├── redis/
    ├── s3/
    └── ai/                      # FastAPI 클라이언트
```

**도메인별 하위 구조 (컨벤션)**

```
<domain>/
├── controller/
├── service/
├── repository/
├── entity/                  # JPA Entity
├── dto/
│   ├── request/
│   └── response/
└── enums/
```

---

## 주요 컨벤션

### 멀티테넌트
- 모든 `Entity` 는 `BaseEntity` 를 상속하여 `tenantId` 를 가진다.
- 모든 PostgreSQL 쿼리는 `tenantId` 필터를 강제한다 (추후 `TenantAwareRepository` 패턴 or Aspect 도입 예정).
- `SecurityContext` 에서 `tenantId` 를 꺼내 사용. Controller 에서 직접 받지 않는다.

### 예외
- `throw new BusinessException(<Domain>ErrorCode.XXX)` 패턴 사용.
- 도메인별 에러는 해당 도메인의 `<Domain>ErrorCode` enum 을 `ErrorCode` 인터페이스로 구현.
- 공통 에러는 `CommonErrorCode` 참조. 메시지 오버라이드가 필요하면 `new BusinessException(errorCode, message)`.

### 응답
- 모든 응답은 `ApiResponse<T>` 로 감싼다.
- 성공: `ApiResponse.ok(data)` / `ApiResponse.ok()` / `ApiResponse.created(data)`
- 실패(도메인): `ApiResponse.fail(errorCode)` / `ApiResponse.fail(errorCode, message)`
- 실패(프레임워크 예외): `ApiResponse.fail(status, message)`

### 로깅
- 로그에 `tenantId`, `userId`, `resourceId` 포함.
- 민감 정보(토큰, 비밀번호, 외부 API raw body) 로그 금지.

### 계층
- Controller → Service → Repository.
- Controller 에서 Repository 직접 호출 금지.
- Entity 를 Response 로 직접 반환 금지 (Service 에서 Response DTO 로 변환).

---

## 테스트

```bash
./gradlew test
./gradlew jacocoTestReport   # (jacoco 플러그인 추가 후)
```

테스트 프로파일은 로컬 PostgreSQL `fint_test` DB를 사용 (`ddl-auto: create-drop`).
CI 에서는 Testcontainers 도입 예정.

테스트 계층 가이드는 `.claude/skills/tdd-workflow/SKILL.md` 참조.

---

## 빌드 · 배포

```bash
# 로컬 jar 빌드
./gradlew bootJar

# Docker 이미지 빌드 (멀티 스테이지)
docker build -t fint-backend:local .

# 실행
docker run -p 8080:8080 --env-file .env fint-backend:local
```

CI/CD 는 루트 `Jenkinsfile` 참조.
