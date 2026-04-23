# F!NT Infra

프로젝트 전체가 사용하는 인프라 스택을 Docker Compose 로 관리한다.

## 디렉터리 구조

```
infra/
├── docker-compose.yml      # 앱 데이터 스택: PostgreSQL + Redis
├── .env.example            # 환경변수 템플릿
├── .gitignore              # .env 제외
├── README.md               # 이 파일
└── monitoring/             # 모니터링 스택 (Prometheus + Grafana + Alertmanager + exporters)
    ├── docker-compose.yml
    └── README.md
```

**앱 스택과 모니터링 스택은 별도 compose 파일로 분리되어 있다.** 필요한 것만 골라서 기동 가능.

---

## 🚀 빠른 시작 (신규 팀원이 봐야 할 최소 단계)

### 전제 조건
- Docker Desktop (Windows/Mac) 또는 Docker Engine + Compose plugin (Linux) 설치
- Git 클론 완료

### 1. 앱 스택 기동 (PostgreSQL + Redis)

```bash
cd infra
cp .env.example .env       # 로컬 개발엔 기본값 그대로 OK
docker compose up -d
docker compose ps          # 두 컨테이너 모두 healthy 확인
```

접속 정보 (로컬 기본값):

| 서비스 | 호스트 포트 | 유저 | 비밀번호 | DB |
| --- | --- | --- | --- | --- |
| PostgreSQL | `localhost:5432` | `fint` | `fint` | `fint_local` |
| Redis | `localhost:6379` | — | *(없음)* | — |

### 2. Spring Boot 기동 (별도 터미널)

Spring 은 **호스트에서 직접 기동**한다 (컨테이너화는 추후 별도 PR).

```bash
cd backend
cp .env.example .env       # 한 번만. 값은 로컬 기본값 그대로 OK
./gradlew bootRun --args='--spring.profiles.active=local'
```

기동되면:
- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Actuator Prometheus: http://localhost:8080/actuator/prometheus

### 3. (선택) 모니터링 스택도 함께 기동

```bash
cd infra/monitoring
cp .env.example .env
echo "https://meeting.ssafy.com/hooks/<팀-webhook-id>" > alertmanager/webhook_url
docker compose up -d
```

상세는 `infra/monitoring/README.md` 참조.

### 4. 정지

```bash
# 앱 스택
cd infra
docker compose down            # 컨테이너만 제거 (데이터는 볼륨에 유지)
docker compose down -v         # 데이터까지 삭제 (초기화)

# 모니터링 스택은 infra/monitoring/ 로 이동 후 동일 명령
```

---

## 📦 앱 스택 상세

### PostgreSQL 16
- **용도**: 메인 DB (고객 / 딜 / 미팅 / 스코어 / 위키 / 감사로그 등)
- **전략**: 정형 필드는 컬럼, 가변 부가정보는 JSONB 하이브리드
- **데이터 영속**: `postgres-data` 볼륨에 저장 → `docker compose down` 해도 유지
- **리소스 제한**: 512MB (로컬 개발용)

### Redis 7
- **용도**: 세션 / 캐시 / 비동기 큐 (Stream)
- **사용 주체**: Spring + FastAPI 양방향 (소유는 Spring)
- **데이터 영속**: `redis-data` 볼륨 + `--save 60 1` (60초마다 변경 1건 이상이면 스냅샷)
- **리소스 제한**: 128MB

### 포함되지 않은 것
| 서비스 | 이유 | 추가 예정 시점 |
| --- | --- | --- |
| Spring Boot | 로컬은 호스트에서 gradle 로 기동이 편함 | Spring 컨테이너화 PR 시 |
| FastAPI | 아직 구현 전 | AI 서비스 착수 시 |
| MongoDB | 원본 저장용, 초기 구현 단계엔 불필요 | 뉴스/DART 원본 저장 로직 착수 시 |
| Neo4j | FastAPI 전담, 아직 FastAPI 전 | GraphRAG 기능 착수 시 |

이 모두 추가될 때는 **이 compose 파일에 서비스를 추가**하거나, 규모가 커지면 별도 compose 파일로 분리한다.

---

## 🔧 자주 쓰는 명령

### PostgreSQL 접속 (psql)

```bash
docker exec -it fint-postgres psql -U fint -d fint_local
```

### Redis 접속 (redis-cli)

```bash
docker exec -it fint-redis redis-cli
```

### 로그 확인

```bash
docker compose logs -f postgres       # 실시간
docker compose logs postgres --tail 50
```

### 컨테이너 상태

```bash
docker compose ps
```

`healthy` 상태가 아니면 기동 중이거나 장애. 로그 확인.

### 데이터 완전 초기화

```bash
docker compose down -v
docker compose up -d
```

볼륨까지 삭제되므로 개발 중 임시 데이터가 날아간다. 주의.

---

## ⚠️ 주의

### 1. `.env` 파일 절대 커밋 금지
`.gitignore` 에 등록되어 있지만, 실수로 `git add -A` 같은 명령 쓰면 들어갈 수 있음. 커밋 전 `git status` 확인.

### 2. 포트 충돌
로컬에 이미 PostgreSQL/Redis 가 돌고 있으면 포트 충돌. `.env` 의 `POSTGRES_PORT`, `REDIS_PORT` 로 조정 가능.

### 3. 모니터링 스택과의 관계
- 앱 스택 네트워크: `fint-app`
- 모니터링 스택 네트워크: `fint-monitoring`
- 두 스택은 **별도 네트워크**. 현재는 Prometheus 가 `host.docker.internal:8080` 으로 호스트의 Spring 을 scrape.
- 추후 Spring 이 컨테이너화되면 두 네트워크를 `external` 로 공유하는 구조로 변경 예정.

### 4. 운영 환경
이 compose 는 **로컬 개발 전용**. 운영(Lightsail)에선 `docker-compose.prod.yml` override 로 별도 설정 (Nginx, TLS, 시크릿 주입 등). 해당 파일은 배포 파이프라인 구축 시 추가.

---

## 📚 관련 문서

- [프로젝트 전체 README](../README.md)
- [Claude Code 가이드](../CLAUDE.md)
- [백엔드 README](../backend/README.md)
- [모니터링 가이드](../docs/monitoring.md)
- [모니터링 스택 README](monitoring/README.md)
