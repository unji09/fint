# F!NT 모니터링 스택 (Phase 0)

Docker Compose로 Prometheus + Grafana + Alertmanager + exporters를 기동한다.

팀 공용 정책 · 지표 정의는 `docs/monitoring.md`를 참조.

## 구성

| 서비스 | 포트 | 역할 |
| --- | --- | --- |
| Prometheus | 9090 | 메트릭 수집 · 저장 (retention 7일) |
| Grafana | 3001 | 시각화 · 대시보드 |
| Alertmanager | 9093 | 알람 라우팅 (→ Mattermost) |
| node_exporter | 내부 | 호스트 메트릭 |
| cAdvisor | 내부 | 컨테이너 메트릭 |

**DB exporter (postgres / redis) 는 Phase 0에 포함되지 않음**. 해당 DB 컨테이너가 compose에 도입되는 시점에 별도 PR로 추가한다.

## 전체 흐름 (한눈에)

### 각 서비스의 역할

| 서비스 | 한 줄 요약 | 쉽게 말하면 |
| --- | --- | --- |
| **node_exporter** | 호스트(서버) 자체의 CPU/메모리/디스크/네트워크 지표 노출 | **체온계** — 서버 본체 상태 측정 |
| **cAdvisor** | 컨테이너별 CPU/메모리/재시작 횟수 지표 노출 | **반장** — 컨테이너 하나하나 상태 보고 |
| **Prometheus** | 위 지표들을 15초마다 수집 · 저장 · 룰 평가 | **창고지기** — 숫자 모아 창고에 쌓고 임계치 감시 |
| **Grafana** | Prometheus의 데이터를 그래프/대시보드로 시각화 | **화가** — 창고의 숫자를 그래프로 그림 |
| **Alertmanager** | 룰 위반 시 알람을 Mattermost로 라우팅 | **전달사** — "이상해!" 신호를 팀 채널로 전송 |

### 데이터 흐름

```
┌───────────────────────────────────────────────────────────┐
│                     [ 지표 소스 ]                          │
│                                                             │
│   호스트 OS ──→ node_exporter                               │
│   컨테이너들 ──→ cAdvisor                                    │
│   Spring Actuator ──(/actuator/prometheus)                 │
└───────────────────────────────────────────────────────────┘
                            │  15초마다 scrape (pull 방식)
                            ↓
                    ┌───────────────┐
                    │  Prometheus   │  ← 저장 + 룰 평가
                    │  (TSDB 7일)    │
                    └───────┬───────┘
                            │
              ┌─────────────┴─────────────┐
              │                            │
              ↓                            ↓
        ┌──────────┐              ┌────────────────┐
        │ Grafana  │              │  Alertmanager  │
        │ (시각화)  │              │  (룰 위반 시)   │
        └──────────┘              └────────┬───────┘
                                           │ webhook
                                           ↓
                                     Mattermost
                                     #alert-fint
```

### 예시 시나리오 — "디스크 사용률 85%" 상황

1. **node_exporter**: "저의 `/` 파티션 사용률 85%입니다" → 메트릭으로 노출
2. **Prometheus**: 15초마다 위 숫자 scrape → DB 저장 → 룰 평가 (`> 80%` 해당)
3. **Alertmanager**: Prometheus로부터 "DiskUsageHigh 발화" 통보 받음 → Mattermost webhook 호출
4. **Mattermost** `#alert-fint`: `[CRITICAL] DiskUsageHigh` 메시지 도착
5. **Grafana** (선택적으로 사용자가 들어와서 확인): 디스크 그래프가 빨간 영역에 진입한 시점 시각적 확인
6. **cAdvisor** (조사 단계): "어느 컨테이너가 디스크를 많이 쓰는지" 조사 시 활용

### 개발자가 실제로 보는 것

| 상황 | 보는 곳 | URL |
| --- | --- | --- |
| 평소 대시보드 확인 | Grafana | http://localhost:3001 |
| "왜 이 엔드포인트 느리지?" | Grafana JVM 대시보드 | http://localhost:3001 |
| "메트릭 직접 쿼리" | Prometheus 쿼리 창 | http://localhost:9090/graph |
| "타겟 UP/DOWN 확인" | Prometheus targets | http://localhost:9090/targets |
| "현재 발화 중인 알람" | Alertmanager | http://localhost:9093 |
| **알람 발생 순간** | **Mattermost `#alert-fint`** | — |

### Pull 방식인 이유

Prometheus는 **"찾아가서 긁어오는"** pull 방식이다. 장점:
- 각 서비스는 자기 지표만 노출하면 됨 (Prometheus 존재 몰라도 됨)
- Prometheus가 다운되어도 애플리케이션 서비스는 영향 없음
- 새 서비스 추가 시 Prometheus 설정에 scrape 타겟 한 줄만 추가

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
| Grafana | http://localhost:3001 | admin / `.env`의 `GRAFANA_ADMIN_PASSWORD` |
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
| PostgreSQL / Redis 컨테이너 도입 | 각 DB별 exporter (`postgres_exporter`, `redis_exporter`) + scrape_config 추가 |
| FastAPI 서비스 착수 | FastAPI scrape 타겟 + `prometheus-fastapi-instrumentator` |
| LLM 호출 코드 머지 | LLM 비용 · 캐시 히트율 메트릭 계측 + 알람 룰 추가 |
| 운영 트래픽 발생 | Loki + Promtail 도입, Retention 14일, 알람 임계치 재조정 |

자세한 내용은 `docs/monitoring.md` 참조.
