# F!NT 모니터링 가이드

## 1. 개요

F!NT는 실시간 서비스가 아니지만 모니터링은 반드시 필요하다. 세 가지 이유.

1. **LLM 비용 추적** — 요청당 여러 번 호출되므로 비용이 실시간으로 누적됨
2. **비동기 파이프라인 지연** — STT → 엔티티 추출 → 위키 생성 체인 중 어디 막히면 사용자 경험 전부 손상
3. **멀티테넌트 격리 위반** — `tenant_id` 필터 누락은 즉시 감지 + 차단 필요 (보안)

관련 요구사항: `REQ-DASH-06 개발자 시스템 대시보드` (`docs/requirements.md`)

## 2. 기술 스택

| 구성 | 역할 |
| --- | --- |
| Prometheus | 메트릭 수집 · 저장 (TSDB) |
| Grafana | 시각화 · 대시보드 |
| Alertmanager | 알람 라우팅 |
| node_exporter | 호스트 CPU/메모리/디스크 |
| cAdvisor | 컨테이너별 리소스 |
| mongodb_exporter | MongoDB 연결/쿼리 지표 |
| redis_exporter | Redis 큐 길이/메모리 |
| Micrometer (Spring) | JVM/HTTP/DB 풀 자동 계측 |
| prometheus-fastapi-instrumentator | FastAPI HTTP 자동 계측 |

알람 채널: **Mattermost** `#alert-fint` (Incoming Webhook)

## 3. Phase 0 — 초기 구성 (구현 시작 시점)

### 포함
- 위 4개 exporter + Spring Actuator + FastAPI instrumentator
- Grafana 대시보드 2개
  - **System Overview**: 노드/컨테이너/DB/Redis 기본 지표
  - **API Performance**: 엔드포인트별 RPS · 응답시간 · 에러율
- Alertmanager 알람 룰 3개 (아래 참조)

### 제외 (확장 시점에 추가)
| 제외 항목 | 추가 시점 |
| --- | --- |
| Loki + Promtail (로그) | 운영 전환 또는 디버깅 필요해질 때 |
| Neo4j exporter | REQ-WIKI-13 시맨틱 검색 착수 시 |
| 커스텀 비즈니스 메트릭 (LLM 비용/STT 큐/위키 실패율) | 해당 기능 PR과 함께 |

**원칙**: 메트릭은 **기능 PR에 함께 들어간다**. 없는 기능의 대시보드/알람은 미리 만들지 않는다.

## 4. 알람 룰

### 초기 3개 (Phase 0)

| 룰 | 조건 | 근거 |
| --- | --- | --- |
| 디스크 사용률 | > 80% | 단일 Lightsail 인스턴스. 디스크 가득 차면 전체 장애 |
| p95 응답시간 | > 500ms (5분 평균) | 성능 목표치(명세 3.2) 위반 |
| 컨테이너 재시작 | > 3회/시간 | OOM 또는 반복 장애 조기 감지 |

### 기능과 함께 추가될 룰

| 룰 | 추가 트리거 |
| --- | --- |
| LLM 일일 비용 > **$2** | 첫 LLM 호출 코드 머지 시 |
| STT 큐 대기 > 30분 | 첫 STT 파이프라인 머지 시 |
| 위키 생성 실패율 > 10% | 첫 위키 자동 생성 코드 머지 시 |
| MongoDB 연결 풀 사용률 > 80% | 트래픽 쌓이기 시작할 때 |
| 테넌트 격리 위반 카운터 증가 | Interceptor 구현 시 (임계치: 5분 내 N건) |

### 임계치 기준 (개발 단계 값)
- LLM 일일 비용 $2: 5명 팀이 정상 테스트하면 안 넘는 수준. 초과 = 루프/실수 의심
- p95 500ms: 명세서 3.2 성능 요구사항 그대로
- Retention 7일: 구현 초기엔 충분. 운영 전환 시 14일로 상향 검토

## 5. 대시보드

### System Overview
- Host: CPU / Memory / Disk / Network
- Container: 컨테이너별 CPU · Memory · 재시작 횟수
- MongoDB: 연결 수, 느린 쿼리, 인덱스 hit ratio
- Redis: 키 수, 메모리, hit ratio

### API Performance
- HTTP RPS (엔드포인트별)
- p50/p95/p99 응답시간
- 상태 코드 분포 (2xx/4xx/5xx)
- JVM: 힙 사용률, GC 빈도
- DB 커넥션 풀 사용률

## 6. 확장 트리거

| 트리거 | 추가할 것 |
| --- | --- |
| 첫 LLM 호출 코드 머지 | LLM 비용 · 캐시 히트율 메트릭 + 알람 |
| 첫 STT 파이프라인 머지 | 큐 길이 · 처리 시간 메트릭 + 알람 |
| 외부 수집 스케줄러 구현 | 뉴스/DART 수집 성공률 메트릭 |
| 운영 트래픽 발생 시작 | Loki + Promtail 도입, Retention 14일, 알람 임계치 재조정 |
| REQ-WIKI-13 (시맨틱 검색) 착수 | Neo4j exporter + Vector Index 쿼리 지연 |

## 7. 운영 정책

- **환경 분리**: 알람 룰은 `prod` 프로파일에만 적용. `dev`/`local`에 적용하면 오탐으로 알람 피로 발생
- **Retention**: 7일 (Phase 0), 운영 전환 시 14일
- **리소스 예산** (16GB Lightsail): Prometheus ~1GB, Grafana ~200MB, Alertmanager ~100MB, exporter 합계 ~200MB
- **장기 저장**: 현재 단일 노드 로컬 저장만. 필요 시 Thanos / VictoriaMetrics 검토 (로드맵)

## 8. 디렉터리 구조 (예정)

```
infra/
└── monitoring/
    ├── docker-compose.yml
    ├── prometheus/
    │   ├── prometheus.yml          # scrape 설정
    │   └── alerts.yml              # 알람 룰
    ├── alertmanager/
    │   └── alertmanager.yml        # Mattermost webhook
    └── grafana/
        └── provisioning/
            ├── datasources/
            └── dashboards/
```

## 9. 관련 문서

- [요구사항 명세서](requirements.md) — `REQ-DASH-06`, `3.2 성능 요구사항`, `3.3 보안 요구사항`
- [인프라 · 배포](infra.md) *(작성 예정)*
- [Claude Code 가이드](../CLAUDE.md)
