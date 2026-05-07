from __future__ import annotations

from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, Field


class WidgetType(StrEnum):
    BAR = "BAR"
    LINE = "LINE"
    PIE = "PIE"
    KPI = "KPI"
    TABLE = "TABLE"


class FilterOperator(StrEnum):
    EQ = "="
    NEQ = "!="
    GT = ">"
    LT = "<"
    GTE = ">="
    LTE = "<="
    IN = "IN"
    LIKE = "LIKE"
    BETWEEN = "BETWEEN"


class FilterCondition(BaseModel):
    column: str
    operator: FilterOperator
    value: str | int | float | list[str | int | float]


class OrderDirection(StrEnum):
    ASC = "ASC"
    DESC = "DESC"


class OrderSpec(BaseModel):
    column: str
    direction: OrderDirection = OrderDirection.ASC


class JoinSpec(BaseModel):
    table: str
    on_self: str
    on_other: str


class QuerySpec(BaseModel):
    table: str
    columns: list[str]
    joins: list[JoinSpec] = Field(default_factory=list)
    filters: list[FilterCondition] = Field(default_factory=list)
    group_by: list[str] | None = None
    order_by: list[OrderSpec] | None = None
    limit: int = Field(default=100, ge=1, le=500)


class SemanticSearchSpec(BaseModel):
    search_text: str
    source_filter: Literal["NEWS", "DART", "ALL"] = "ALL"
    top_k: int = Field(default=10, ge=1, le=50)


class IntentResult(BaseModel):
    search_type: Literal["STRUCTURED", "SEMANTIC", "HYBRID", "REJECTED"]
    query_spec: QuerySpec | None = None
    semantic_spec: SemanticSearchSpec | None = None
    suggested_title: str
    rejection_reason: str | None = None


class WidgetConfig(BaseModel):
    colors: list[str] | None = None
    x_label: str | None = None
    y_label: str | None = None
    period: str | None = None


class InsightResult(BaseModel):
    widget_type: WidgetType
    title: str
    insight_text: str
    key_findings: list[str]
    data_summary: str
    config: WidgetConfig


class DashboardQueryRequest(BaseModel):
    trace_id: str
    action: Literal["CREATE", "ADD", "MODIFY"]
    input_text: str
    dashboard_id: int
    tenant_id: int
    user_id: int
    existing_widgets: list[dict] = Field(default_factory=list)
    current_widget: dict | None = None


class QueryStatus(StrEnum):
    INTENT_PARSING = "INTENT_PARSING"
    DATA_QUERYING = "DATA_QUERYING"
    COMPONENT_BUILDING = "COMPONENT_BUILDING"
    STYLING = "STYLING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
