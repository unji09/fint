# F!NT Backend (Spring Boot)

메인 API 서버. 비즈니스 로직 · 인증 · CRUD · 파일 · 스케줄링 · 데이터 수집 담당.

AI 관련 처리는 내부망의 FastAPI 로 위임 (`ai.server-url`).

---

## 기술 스택

- Java 21, Gradle 8.14
- Spring Boot 3.5.13
- Spring Data MongoDB · Spring Data Redis
- Spring Security + JWT (`jjwt 0.12.6`)
- Springdoc OpenAPI 2.8.4
- AWS SDK for Java v2 (S3)

---

## 빠른 시작

```bash
# 1. 환경변수 준비
cp .env.example .env   # 값 채우기

# 2. 로컬 의존성 기동 (Mongo + Redis)
#    프로젝트 루트의 infra/ 디렉토리에 docker-compose 가 있다면 그걸 쓰고,
#    없다면 임시로:
docker run -d --name mongo -p 27017:27017 mongo:7
docker run -d --name redis -p 6379:6379 redis:7

# 3. 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

Swagger: http://localhost:8080/swagger-ui.html

---

## 프로파일

| Profile | 용도 | DB |
|---------|------|----|
| `local` | 개발자 로컬 | 로컬 도커 Mongo/Redis |
| `dev` | 상시 개발 서버 | Lightsail 공용 Mongo/Redis |
| `prod` | 실서비스 | Lightsail 전용 Mongo/Redis |

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
│   │   └── document/            # BaseDocument (멀티테넌트 + 타임스탬프)
│   ├── config/                  # MongoConfig, OpenApiConfig, SecurityConfig, RestTemplateConfig
│   └── exception/               # 5종 예외 + GlobalExceptionHandler
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
    ├── mongo/
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
├── document/                # MongoDB Document (JPA의 entity 대신)
├── dto/
│   ├── request/
│   └── response/
└── enums/
```

---

## 주요 컨벤션

### 멀티테넌트
- 모든 `Document` 는 `BaseDocument` 를 상속하여 `tenantId` 를 가진다.
- 모든 MongoDB 쿼리는 `tenantId` 필터를 강제한다 (추후 `TenantAwareRepository` 패턴 or Aspect 도입 예정).
- `SecurityContext` 에서 `tenantId` 를 꺼내 사용. Controller 에서 직접 받지 않는다.

### 예외
- `BadRequestException`, `UnauthorizedException`, `ForbiddenException`, `NotFoundException`, `ConflictException` 중 적절한 것 사용.
- 새로운 케이스는 원칙적으로 위 5개 중 하나로 매핑. 꼭 필요할 때만 신규 예외 추가.

### 응답
- 모든 응답은 `ApiResponse<T>` 로 감싼다.
- `ApiResponse.ok(data)` / `ApiResponse.created(data)` / `ApiResponse.fail(status, message)`.

### 로깅
- 로그에 `tenantId`, `userId`, `resourceId` 포함.
- 민감 정보(토큰, 비밀번호, 외부 API raw body) 로그 금지.

### 계층
- Controller → Service → Repository.
- Controller 에서 Repository 직접 호출 금지.
- Document 를 Response 로 직접 반환 금지 (Service 에서 Response DTO 로 변환).

---

## 테스트

```bash
./gradlew test
./gradlew jacocoTestReport   # (jacoco 플러그인 추가 후)
```

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
