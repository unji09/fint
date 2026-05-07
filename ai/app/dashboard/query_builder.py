"""QuerySpec → parameterized SQL 조립. 허용 목록 대조 + tenant_id 강제 주입."""

from __future__ import annotations

import re

from app.dashboard.schema_context import (
    ALLOWED_AGGREGATES,
    ALLOWED_TABLES,
    get_allowed_columns,
    get_allowed_join_targets,
    get_allowed_table_names,
    get_filterable_columns,
)
from app.schemas.dashboard import FilterOperator, QuerySpec

_AGG_PATTERN = re.compile(r"^(COUNT|SUM|AVG|MIN|MAX)\((\*|\w+)\)$", re.IGNORECASE)


class QueryBuildError(Exception):
    pass


def build_query(spec: QuerySpec, *, tenant_id: int) -> tuple[str, dict]:
    _validate_spec(spec)

    params: dict[str, object] = {}
    param_idx = 0

    def _next_param(value: object) -> str:
        nonlocal param_idx
        param_idx += 1
        key = f"p{param_idx}"
        params[key] = value
        return f":{key}"

    table_meta = ALLOWED_TABLES[spec.table]

    select_exprs: list[str] = []
    for col in spec.columns:
        agg_match = _AGG_PATTERN.match(col)
        if agg_match:
            func, arg = agg_match.group(1).upper(), agg_match.group(2)
            if arg == "*":
                select_exprs.append(f"{func}(*)")
            else:
                select_exprs.append(f"{func}({spec.table}.{arg})")
        else:
            select_exprs.append(f"{spec.table}.{col}")

    sql_parts = [f"SELECT {', '.join(select_exprs)}", f"FROM {spec.table}"]

    # tenant_id 격리 JOIN (무조건)
    if table_meta.tenant_path and table_meta.tenant_path.joins:
        prev_table = spec.table
        for join_table, self_col, join_col in table_meta.tenant_path.joins:
            sql_parts.append(f"JOIN {join_table} ON {prev_table}.{self_col} = {join_table}.{join_col}")
            prev_table = join_table
        tenant_table = table_meta.tenant_path.joins[-1][0]
        placeholder = _next_param(tenant_id)
        sql_parts.append(f"WHERE {tenant_table}.{table_meta.tenant_path.tenant_column} = {placeholder}")
    elif table_meta.tenant_path:
        placeholder = _next_param(tenant_id)
        sql_parts.append(f"WHERE {spec.table}.tenant_id = {placeholder}")
    else:
        placeholder = _next_param(tenant_id)
        sql_parts.append(f"WHERE {spec.table}.tenant_id = {placeholder}")

    # soft delete 필터
    if table_meta.has_soft_delete:
        sql_parts.append(f"AND {spec.table}.is_deleted = FALSE")

    # 사용자 지정 JOIN
    for join in spec.joins:
        sql_parts.append(f"JOIN {join.table} ON {spec.table}.{join.on_self} = {join.table}.{join.on_other}")

    # 사용자 필터
    for f in spec.filters:
        col_ref = f"{spec.table}.{f.column}"
        if f.operator == FilterOperator.IN:
            if not isinstance(f.value, list):
                raise QueryBuildError("IN 연산자에는 리스트가 필요합니다")
            placeholders = ", ".join(_next_param(v) for v in f.value)
            sql_parts.append(f"AND {col_ref} IN ({placeholders})")
        elif f.operator == FilterOperator.BETWEEN:
            if not isinstance(f.value, list) or len(f.value) != 2:
                raise QueryBuildError("BETWEEN 연산자에는 2개 값의 리스트가 필요합니다")
            p1 = _next_param(f.value[0])
            p2 = _next_param(f.value[1])
            sql_parts.append(f"AND {col_ref} BETWEEN {p1} AND {p2}")
        else:
            placeholder = _next_param(f.value)
            sql_parts.append(f"AND {col_ref} {f.operator.value} {placeholder}")

    # GROUP BY
    if spec.group_by:
        group_cols = ", ".join(f"{spec.table}.{c}" for c in spec.group_by)
        sql_parts.append(f"GROUP BY {group_cols}")

    # ORDER BY
    if spec.order_by:
        order_exprs = [f"{spec.table}.{o.column} {o.direction.value}" for o in spec.order_by]
        sql_parts.append(f"ORDER BY {', '.join(order_exprs)}")

    # LIMIT
    limit_placeholder = _next_param(spec.limit)
    sql_parts.append(f"LIMIT {limit_placeholder}")

    return " ".join(sql_parts), params


def _validate_spec(spec: QuerySpec) -> None:
    allowed_tables = get_allowed_table_names()
    if spec.table not in allowed_tables:
        raise QueryBuildError(f"허용되지 않은 테이블: {spec.table}")

    allowed_cols = get_allowed_columns(spec.table)
    filterable_cols = get_filterable_columns(spec.table)

    for col in spec.columns:
        agg_match = _AGG_PATTERN.match(col)
        if agg_match:
            func, arg = agg_match.group(1).upper(), agg_match.group(2)
            if func not in ALLOWED_AGGREGATES:
                raise QueryBuildError(f"허용되지 않은 집계 함수: {func}")
            if arg != "*" and arg not in allowed_cols:
                raise QueryBuildError(f"허용되지 않은 컬럼: {spec.table}.{arg}")
        elif col not in allowed_cols:
            raise QueryBuildError(f"허용되지 않은 컬럼: {spec.table}.{col}")

    for f in spec.filters:
        if f.column not in filterable_cols:
            if f.column in allowed_cols:
                raise QueryBuildError(f"필터링할 수 없는 컬럼: {spec.table}.{f.column}")
            raise QueryBuildError(f"허용되지 않은 컬럼: {spec.table}.{f.column}")

    if spec.order_by:
        for o in spec.order_by:
            if o.column not in allowed_cols:
                raise QueryBuildError(f"허용되지 않은 컬럼: {spec.table}.{o.column}")

    if spec.group_by:
        for col in spec.group_by:
            if col not in allowed_cols:
                raise QueryBuildError(f"허용되지 않은 컬럼: {spec.table}.{col}")

    allowed_joins = get_allowed_join_targets(spec.table)
    for join in spec.joins:
        if join.table not in allowed_joins:
            raise QueryBuildError(f"허용되지 않은 JOIN: {spec.table} → {join.table}")
        expected = allowed_joins[join.table]
        if join.on_self != expected.self_column or join.on_other != expected.target_column:
            raise QueryBuildError(
                f"허용되지 않은 JOIN 컬럼: {join.on_self}={join.on_other}, "
                f"허용된 경로: {expected.self_column}={expected.target_column}"
            )
