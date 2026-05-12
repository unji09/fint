"""CRM 스키마 메타데이터 — LLM 시스템 프롬프트와 QuerySpec 허용 목록."""

from __future__ import annotations

from dataclasses import dataclass, field

ALLOWED_TABLES: dict[str, TableMeta] = {}


@dataclass(frozen=True)
class ColumnMeta:
    name: str
    type: str
    description: str
    filterable: bool = True


@dataclass(frozen=True)
class JoinPath:
    target_table: str
    self_column: str
    target_column: str


@dataclass(frozen=True)
class TenantPath:
    joins: list[tuple[str, str, str]]
    """[(join_table, self_col, join_col), ...] to reach tenant_id."""
    tenant_column: str = "tenant_id"


@dataclass(frozen=True)
class TableMeta:
    name: str
    description: str
    columns: list[ColumnMeta]
    allowed_joins: list[JoinPath] = field(default_factory=list)
    tenant_path: TenantPath | None = None
    has_soft_delete: bool = False


ALLOWED_AGGREGATES = frozenset({"COUNT", "SUM", "AVG", "MIN", "MAX"})


def _build_schema() -> dict[str, TableMeta]:
    tables: dict[str, TableMeta] = {}

    tables["accounts"] = TableMeta(
        name="accounts",
        description="고객사/거래처",
        columns=[
            ColumnMeta("account_id", "BIGINT", "고객사 PK"),
            ColumnMeta("name", "VARCHAR", "고객사명"),
            ColumnMeta("industry", "VARCHAR", "업종"),
            ColumnMeta("biz_no", "VARCHAR", "사업자등록번호"),
            ColumnMeta("created_at", "TIMESTAMPTZ", "생성일"),
            ColumnMeta("updated_at", "TIMESTAMPTZ", "수정일"),
        ],
        allowed_joins=[
            JoinPath("deals", "account_id", "account_id"),
            JoinPath("contacts", "account_id", "account_id"),
            JoinPath("account_external_info", "account_id", "account_id"),
            JoinPath("temperature_history", "account_id", "account_id"),
        ],
        tenant_path=TenantPath(
            joins=[
                ("account_user_assignment", "account_id", "account_id"),
                ("users", "user_id", "user_id"),
            ]
        ),
        has_soft_delete=True,
    )

    tables["deals"] = TableMeta(
        name="deals",
        description="영업 딜/거래",
        columns=[
            ColumnMeta("deal_id", "BIGINT", "딜 PK"),
            ColumnMeta("account_id", "BIGINT", "고객사 ID"),
            ColumnMeta("team_id", "BIGINT", "담당팀 ID"),
            ColumnMeta("title", "VARCHAR", "딜 제목"),
            ColumnMeta("expected_close", "DATE", "예상 종료일"),
            ColumnMeta("amount", "NUMERIC", "거래 금액"),
            ColumnMeta("probability", "SMALLINT", "성공 확률 (%)"),
            ColumnMeta("won_at", "TIMESTAMPTZ", "수주 일시"),
            ColumnMeta("lost_at", "TIMESTAMPTZ", "실주 일시"),
            ColumnMeta("lost_reason", "TEXT", "실주 사유"),
            ColumnMeta("current_pipeline", "VARCHAR", "현재 파이프라인 단계"),
            ColumnMeta("created_at", "TIMESTAMPTZ", "생성일"),
            ColumnMeta("updated_at", "TIMESTAMPTZ", "수정일"),
        ],
        allowed_joins=[
            JoinPath("accounts", "account_id", "account_id"),
            JoinPath("activities", "deal_id", "deal_id"),
        ],
        tenant_path=TenantPath(
            joins=[("teams", "team_id", "team_id")]
        ),
        has_soft_delete=True,
    )

    tables["activities"] = TableMeta(
        name="activities",
        description="영업 활동 (미팅, 전화, 업무, 이메일)",
        columns=[
            ColumnMeta("activity_id", "BIGINT", "활동 PK"),
            ColumnMeta("deal_id", "BIGINT", "딜 ID"),
            ColumnMeta("user_id", "BIGINT", "활동 소유자 ID"),
            ColumnMeta("pipeline_stage_id", "BIGINT", "파이프라인 단계 ID"),
            ColumnMeta("type", "VARCHAR", "활동 유형 (MEETING/CALL/TASK/EMAIL)"),
            ColumnMeta("title", "VARCHAR", "활동 제목"),
            ColumnMeta("memo", "TEXT", "활동 메모"),
            ColumnMeta("device_event_id", "VARCHAR", "외부 캘린더 동기화 ID"),
            ColumnMeta("start_at", "TIMESTAMPTZ", "시작 일시"),
            ColumnMeta("end_at", "TIMESTAMPTZ", "종료 일시"),
            ColumnMeta("stt_status", "VARCHAR", "음성인식 상태 (PENDING/PROCESSING/COMPLETED/FAILED)"),
            ColumnMeta("attendees", "JSONB", "참석자 정보", filterable=False),
            ColumnMeta("transcript", "JSONB", "음성 기록", filterable=False),
            ColumnMeta("summary", "JSONB", "회의 요약", filterable=False),
            ColumnMeta("created_at", "TIMESTAMPTZ", "생성일"),
            ColumnMeta("updated_at", "TIMESTAMPTZ", "수정일"),
        ],
        allowed_joins=[
            JoinPath("deals", "deal_id", "deal_id"),
            JoinPath("pipeline_stages", "pipeline_stage_id", "pipeline_stage_id"),
            JoinPath("users", "user_id", "user_id"),
        ],
        tenant_path=TenantPath(joins=[("users", "user_id", "user_id")]),
        has_soft_delete=False,
    )

    tables["contacts"] = TableMeta(
        name="contacts",
        description="고객사 담당자/의사결정자",
        columns=[
            ColumnMeta("contact_id", "BIGINT", "담당자 PK"),
            ColumnMeta("account_id", "BIGINT", "고객사 ID"),
            ColumnMeta("name", "VARCHAR", "담당자명"),
            ColumnMeta("title", "VARCHAR", "직함"),
            ColumnMeta("phone", "VARCHAR", "전화번호"),
            ColumnMeta("email", "VARCHAR", "이메일"),
            ColumnMeta("personality", "TEXT", "담당자 성향/성격 프로필"),
            ColumnMeta("created_at", "TIMESTAMPTZ", "생성일"),
            ColumnMeta("updated_at", "TIMESTAMPTZ", "수정일"),
        ],
        allowed_joins=[
            JoinPath("accounts", "account_id", "account_id"),
        ],
        tenant_path=TenantPath(
            joins=[
                ("accounts", "account_id", "account_id"),
                ("account_user_assignment", "account_id", "account_id"),
                ("users", "user_id", "user_id"),
            ]
        ),
        has_soft_delete=True,
    )

    tables["account_external_info"] = TableMeta(
        name="account_external_info",
        description="고객사 외부 정보 (뉴스, DART 공시)",
        columns=[
            ColumnMeta("account_external_info_id", "BIGINT", "외부정보 PK"),
            ColumnMeta("account_id", "BIGINT", "고객사 ID"),
            ColumnMeta("source", "VARCHAR", "출처 (NEWS, DART)"),
            ColumnMeta("title", "VARCHAR", "제목"),
            ColumnMeta("content", "TEXT", "본문"),
            ColumnMeta("url", "VARCHAR", "원문 URL"),
            ColumnMeta("occurred_at", "TIMESTAMPTZ", "발생 일시"),
        ],
        allowed_joins=[
            JoinPath("accounts", "account_id", "account_id"),
        ],
        tenant_path=TenantPath(
            joins=[
                ("accounts", "account_id", "account_id"),
                ("account_user_assignment", "account_id", "account_id"),
                ("users", "user_id", "user_id"),
            ]
        ),
        has_soft_delete=False,
    )

    tables["temperature_history"] = TableMeta(
        name="temperature_history",
        description="고객사 관계 온도 이력",
        columns=[
            ColumnMeta("temperature_history_id", "BIGINT", "온도이력 PK"),
            ColumnMeta("account_id", "BIGINT", "고객사 ID"),
            ColumnMeta("mood", "VARCHAR", "고객 분위기/온도 상태"),
            ColumnMeta("reason", "TEXT", "변경 사유"),
            ColumnMeta("created_at", "TIMESTAMPTZ", "생성일"),
        ],
        allowed_joins=[
            JoinPath("accounts", "account_id", "account_id"),
        ],
        tenant_path=TenantPath(
            joins=[
                ("accounts", "account_id", "account_id"),
                ("account_user_assignment", "account_id", "account_id"),
                ("users", "user_id", "user_id"),
            ]
        ),
        has_soft_delete=False,
    )

    tables["pipeline_stages"] = TableMeta(
        name="pipeline_stages",
        description="딜 파이프라인 단계 정의",
        columns=[
            ColumnMeta("pipeline_stage_id", "BIGINT", "단계 PK"),
            ColumnMeta("tenant_id", "BIGINT", "테넌트 ID"),
            ColumnMeta("name", "VARCHAR", "단계명"),
            ColumnMeta("sort_order", "INT", "정렬 순서"),
            ColumnMeta("created_at", "TIMESTAMPTZ", "생성일"),
        ],
        allowed_joins=[],
        tenant_path=TenantPath(joins=[]),
        has_soft_delete=False,
    )

    return tables


ALLOWED_TABLES = _build_schema()


def get_allowed_columns(table_name: str) -> set[str]:
    meta = ALLOWED_TABLES.get(table_name)
    if not meta:
        return set()
    return {c.name for c in meta.columns}


def get_filterable_columns(table_name: str) -> set[str]:
    meta = ALLOWED_TABLES.get(table_name)
    if not meta:
        return set()
    return {c.name for c in meta.columns if c.filterable}


def get_allowed_table_names() -> set[str]:
    return set(ALLOWED_TABLES.keys())


def get_allowed_join_targets(table_name: str) -> dict[str, JoinPath]:
    meta = ALLOWED_TABLES.get(table_name)
    if not meta:
        return {}
    return {j.target_table: j for j in meta.allowed_joins}


def build_llm_schema_prompt() -> str:
    """LLM 시스템 프롬프트에 포함할 CRM 스키마 메타데이터."""
    lines = ["## CRM Database Schema\n"]
    for meta in ALLOWED_TABLES.values():
        lines.append(f"### {meta.name} — {meta.description}")
        for col in meta.columns:
            filterable_note = "" if col.filterable else " [SELECT 전용, 필터 불가]"
            lines.append(f"  - {col.name} ({col.type}): {col.description}{filterable_note}")
        if meta.allowed_joins:
            join_names = [j.target_table for j in meta.allowed_joins]
            lines.append(f"  - JOIN 가능: {', '.join(join_names)}")
        if meta.has_soft_delete:
            lines.append("  - ※ 삭제된 행은 자동 제외됨")
        lines.append("")

    lines.append("## 집계 함수")
    lines.append("GROUP BY 사용 시 다음 집계 함수를 columns에 포함할 수 있습니다:")
    lines.append(f"  - {', '.join(sorted(ALLOWED_AGGREGATES))}(column_name) 또는 COUNT(*)")
    lines.append("")

    lines.append("## Cross-Table Column Reference")
    lines.append("JOIN된 테이블의 컬럼을 참조할 때는 `table.column` 형식을 사용하세요.")
    lines.append("예: deals 테이블에서 고객사명으로 필터링할 때")
    lines.append('  - joins에 {"table": "accounts", "on_self": "account_id", "on_other": "account_id"} 추가')
    lines.append('  - filters의 column에 "accounts.name" 으로 지정')
    lines.append('  - columns에도 "accounts.name" 형식으로 사용 가능')
    lines.append("")

    return "\n".join(lines)
