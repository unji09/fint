# F!NT 모니터링 스택 (Phase 0)

Docker Compose로 Prometheus + Grafana + Alertmanager + exporters를 기동한다.

팀 공용 정책 · 지표 정의는 `docs/monitoring.md`를 참조.

## 구성

| 서비스 | 포트 | 역할 |
| --- | --- | --- |
| Prometheus | 9090 | 메트릭 수집 · 저장 (retention 7일) |
| Grafana | 3000 | 시각화 · 대시보드 |
| Alertmanager | 9093 | 알람 라우팅 (→ Mattermost) |
| node_exporter | 내부 | 호스트 메트릭 |
| cAdvisor | 내부 | 컨테이너 메트릭 |

**MongoDB/Redis exporter는 Phase 0에 포함되지 않음**. 해당 컨테이너가 compose에 도입되는 시점에 별도 PR로 추가한다.

## 기동 전 준비

### 1. Grafana 관리자 비밀번호

```bash
cp .env.example .env
# .env 열어서 GRAFANA_ADMIN_PASSWORD를 실제 값으로 변경
```

### 2. Mattermost Webhook URL (알람 수신 필수)

Mattermost `#alert-fint` 채널에서 Incoming Webhook 생성 후 URL을 파일로 저장한다.

```bash
echo "https://meeting.ssafy.com/hooks/your-webhook-id" > alertmanager/webhook_url
```

이 파일은 `.gitignore`에 포함되어 있다. 개인마다 생성 필요.

## 기동

```bash
cd infra/monitoring
docker compose up -d
docker compose ps   # 모든 컨테이너 running 확인
```

## 접속

| 서비스 | URL | 로그인 |
| --- | --- | --- |
| Grafana | http://localhost:3000 | admin / `.env`의 `GRAFANA_ADMIN_PASSWORD` |
| Prometheus | http://localhost:9090/targets | 모든 타겟 UP 확인 |
| Alertmanager | http://localhost:9093 | - |

## 대시보드 최초 import

Grafana UI → `Dashboards > New > Import` → ID 입력:

| 이름 | ID | 용도 |
| --- | --- | --- |
| Node Exporter Full | 1860 | 호스트 CPU/메모리/디스크 |
| JVM (Micrometer) | 4701 | Spring Actuator JVM/HTTP 메트릭 |
| Docker cAdvisor | 14282 | 컨테이너별 리소스 |

Import 시 데이터소스는 `Prometheus` 선택.

## Spring Actuator scrape 검증

Spring이 호스트 8080에서 기동 중이어야 Prometheus가 `fint-backend` 타겟을 scrape할 수 있다.

```bash
cd ../../backend
./gradlew bootRun
```

이후 `http://localhost:9090/targets` 에서 `fint-backend`가 `UP` 상태인지 확인.

## 알람 테스트

```bash
curl -XPOST http://localhost:9093/api/v2/alerts \
  -H 'Content-Type: application/json' \
  -d '[{
    "labels": {"alertname":"TestAlert","severity":"warning"},
    "annotations": {"summary":"Alertmanager 수신 테스트","description":"정상 동작 확인용"}
  }]'
```

Mattermost `#alert-fint` 채널에 메시지 도착 확인.

## 정지 및 정리

```bash
docker compose down              # 컨테이너만 제거
docker compose down -v           # 볼륨(누적된 메트릭)까지 삭제
```

## 환경별 주의사항

### Windows / Mac (Docker Desktop)
- `node_exporter`가 **Docker VM의 메트릭**을 노출 (실제 Windows/Mac 호스트가 아님)
- cAdvisor는 Docker Desktop 환경에서 일부 metric이 비어 있을 수 있음
- 로컬 개발 참고용으로만 사용. 운영 환경(Linux)에선 정상 동작

### Linux (운영: Lightsail)
- 실제 호스트 메트릭 정상 수집
- `extra_hosts: host-gateway`로 Spring 컨테이너화 전까지 호스트 scrape 지원

## 확장 트리거 (Phase 1 이후)

| 트리거 | 추가 작업 |
| --- | --- |
| MongoDB/Redis 컨테이너 도입 | `mongodb_exporter`, `redis_exporter` 서비스 + scrape_config 추가 |
| FastAPI 서비스 착수 | FastAPI scrape 타겟 + `prometheus-fastapi-instrumentator` |
| LLM 호출 코드 머지 | LLM 비용 · 캐시 히트율 메트릭 계측 + 알람 룰 추가 |
| 운영 트래픽 발생 | Loki + Promtail 도입, Retention 14일, 알람 임계치 재조정 |

자세한 내용은 `docs/monitoring.md` 참조.
