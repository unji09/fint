# F!NT Backend (Spring Boot)

메인 API 서버. 비즈니스 로직 · 인증 · CRUD · 파일 메타 · 스케줄링 · 외부 데이터 수집(DART/뉴스) 담당.

AI 관련 처리는 내부망의 FastAPI 로 위임 (`ai.server-url`).

---

## 기술 스택

- Java 21, Gradle 8.14
- Spring Boot 4.0.5
- Spring Data JPA (PostgreSQL) · Spring Data Redis
- Spring Security + JWT (`jjwt 0.12.6`)
- Flyway (DB 스키마 버전 관리)
- Testcontainers (테스트용 DB 자동 기동)
- Micrometer + Prometheus (메트릭 수집)
- Springdoc OpenAPI 3.0.3
- AWS SDK for Java v2 (S3)

---

## 로컬 환경 세팅 (Windows)

> Docker로 DB/Redis 환경을 통일하여 팀원 간 "내 PC에서는 되는데" 문제를 방지한다.

### 1. Docker Desktop 설치

각자 PC에 PostgreSQL, Redis를 따로 설치하면 버전/포트/계정이 제각각이 된다.
Docker로 컨테이너를 띄우면 전원 동일한 환경에서 실행 가능.

[docker.com](https://docker.com) 에서 설치.

### 2. make 설치

`make up`, `make down` 같은 명령어로 Docker 컨테이너를 한 줄로 제어하기 위한 도구.

**PowerShell**에서 실행:

```powershell
# Scoop (Windows 패키지 관리자) 설치
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression

# make 설치
scoop install make
```

### 3. Git Bash에 make 경로 등록

Scoop이 설치한 make를 Git Bash가 인식하지 못할 수 있다.
`.bashrc`에 경로를 추가하면 Git Bash를 열 때마다 자동 인식.

**Git Bash**에서 실행 (한 번만):

```bash
echo 'export PATH="$HOME/scoop/shims:$PATH"' >> ~/.bashrc
```

### 4. IntelliJ 터미널을 Git Bash로 변경

Makefile은 Linux 쉘 기반이라 IntelliJ 기본 터미널(PowerShell)에서는 동작하지 않는다.
Git Bash로 바꿔야 `make` 명령어 사용 가능.

`Ctrl+Alt+S` → Terminal → Shell path:

```
"C:\Program Files\Git\bin\bash.exe" --login -i
```

IntelliJ 재시작. (`--login -i`는 3번에서 등록한 `.bashrc`를 자동 로드하는 옵션)

### 5. 실행

Docker Desktop 실행 후, IntelliJ 터미널에서:

```bash
# 프로젝트 루트로 이동
cd /c/Users/{사용자명}/Desktop/project/S14P31A301

# 환경 점검 (최초 1회)
make doctor

# PostgreSQL + Redis 컨테이너 기동
make up
```

컨테이너 healthy 확인 후, 아래 **둘 중 하나**로 앱 실행:

```bash
# 방법 A: 터미널에서 실행
make backend

# 방법 B: IntelliJ ▶ 버튼으로 FintApplication 실행 (디버깅 가능)
```

Swagger: http://localhost:8080/swagger-ui.html

### 6. 종료

```bash
# 컨테이너 중지
make down

# 컨테이너 + DB 데이터 완전 초기화 (문제 생겼을 때)
make clean
```

---

## 프로파일

| Profile | 용도 | DB | DDL |
|---------|------|----|----|
| `local` | 개발자 로컬 | Docker PostgreSQL/Redis | `validate` |
| `dev` | EC2 배포 서버 | Docker Compose PostgreSQL/Redis | `validate` |
| `test` | 테스트 | Testcontainers (자동 기동) | `none` (Flyway 관리) |

모든 값은 `.env` 또는 환경변수로 주입. `application-*.yml` 에는 기본값 + `${VAR:default}` 패턴만 둔다.

---

## DB 마이그레이션 (Flyway)

스키마 변경은 Flyway SQL 파일로 버전 관리한다. `ddl-auto: update` (Hibernate 자동 생성)는 사용하지 않음.

### 마이그레이션 파일 규칙

- 위치: `src/main/resources/db/migration/`
- 파일명: `V{번호}__{설명}.sql` (더블 언더스코어 `__` 필수)
- 예시: `V2__create_tenant_table.sql`, `V3__add_account_columns.sql`

### 워크플로우

1. Entity 클래스 작성/수정
2. 대응하는 마이그레이션 SQL 파일 작성 (Entity PR에 반드시 포함)
3. `make clean && make up` → 앱 실행으로 검증 (깨끗한 DB에서 전체 마이그레이션 실행)

### 주의사항

- **머지된 마이그레이션 파일은 절대 수정 금지** — 새 마이그레이션으로 변경
- 기존 로컬 DB가 있으면 `make clean` 으로 초기화 권장 (Flyway가 깨끗하게 재시작)
- `baseline-on-migrate: true` (local) — 기존 DB가 있어도 Flyway가 V1부터 추적 시작

---

## 패키지 구조

```
com.ssafy.fint/
├── FintApplication.java
├── global/
│   ├── ApiResponse.java
│   ├── common/
│   │   ├── constant/            # ApiPath, ErrorMessage
│   │   └── entity/              # BaseEntity (id+createdAt), BaseUpdatableEntity (+updatedAt), BaseSoftDeletableEntity (+deletedAt)
│   ├── config/                  # JpaConfig, OpenApiConfig, SecurityConfig, RestTemplateConfig
│   └── exception/               # ErrorCode + BusinessException + GlobalExceptionHandler
│
├── auth/                        # 인증 · JWT
├── tenant/                      # 테넌트 관리
├── customer/                    # 고객사
├── deal/                        # 딜
├── meeting/                     # 미팅 (녹음 + STT)
├── signal/                      # 영업 시그널 (뉴스 · DART)
└── dashboard/                   # 대시보드
```

**도메인별 하위 구조 (컨벤션)**

```
<domain>/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
│   ├── request/
│   └── response/
└── enums/
```

---

## 주요 컨벤션

### 멀티테넌트 (미구현 — 구현 시 아래 규칙 적용)
- 모든 Entity 에 `tenantId` 를 추가하고, 모든 쿼리에 `tenantId` 필터를 강제한다.
- `SecurityContext` 에서 `tenantId` 를 꺼내 사용. Controller 에서 직접 받지 않는다.

### 예외
- `throw new BusinessException(<Domain>ErrorCode.XXX)` 패턴 사용.
- 도메인별 에러는 해당 도메인의 `<Domain>ErrorCode` enum 을 `ErrorCode` 인터페이스로 구현.
- 공통 에러는 `CommonErrorCode` 참조.

### 응답
- 모든 응답은 `ApiResponse<T>` 로 감싼다.
- 성공: `ApiResponse.ok(data)` / `ApiResponse.ok()` / `ApiResponse.created(data)`
- 실패: `ApiResponse.fail(errorCode)` / `ApiResponse.fail(errorCode, message)`

### 계층
- Controller → Service → Repository.
- Controller 에서 Repository 직접 호출 금지.
- Entity 를 Response 로 직접 반환 금지 (Service 에서 Response DTO 로 변환).

---

## 테스트

```bash
./gradlew test
```

Testcontainers가 Docker로 PostgreSQL을 자동 기동. Docker Desktop 실행 상태에서 테스트.

---

## 빌드 · 배포

```bash
# 로컬 jar 빌드
./gradlew bootJar

# Docker 이미지 빌드 (멀티 스테이지)
docker build -t fint-backend:local .
```

CI/CD는 루트 `Jenkinsfile` 참조. Jenkins CD는 `Dockerfile.runtime` (JAR만 복사하는 경량 이미지) 사용.
