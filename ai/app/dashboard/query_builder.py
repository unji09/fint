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

_AGG_PATTERN = re.compile(r"^(COUNT|SUM|AVG|MIN|MAX)\((\*|\w+(?:\.\w+)?)\)$", re.IGNORECASE)
_DATE_TRUNC_PATTERN = re.compile(r"^DATE_TRUNC\('(\w+)',\s*(\w+(?:\.\w+)?)\)$", re.IGNORECASE)
_DOT_COL_PATTERN = re.compile(r"^(\w+)\.(\w+)$")
_ALIAS_PATTERN = re.compile(r"\s+[Aa][Ss]\s+\w+$")

_ALLOWED_DATE_TRUNC_INTERVALS = frozenset({
    "microsecond", "millisecond", "second", "minute", "hour",
    "day", "week", "month", "quarter", "year",
})


class QueryBuildError(Exception):
    pass


def _validate_date_trunc_interval(interval: str) -> None:
    if interval.lower() not in _ALLOWED_DATE_TRUNC_INTERVALS:
        raise QueryBuildError(f"허용되지 않은 DATE_TRUNC 간격: {interval}")


def _get_available_tables(spec: QuerySpec) -> set[str]:
    tables = {spec.table}
    for join in spec.joins:
        tables.add(join.table)
    table_meta = ALLOWED_TABLES.get(spec.table)
    if table_meta and table_meta.tenant_path:
        for join_table, _, _ in table_meta.tenant_path.joins:
            tables.add(join_table)
    return tables


def _resolve_column(col: str, spec: QuerySpec) -> tuple[str, str]:
    dot_match = _DOT_COL_PATTERN.match(col)
    if dot_match:
        table, column = dot_match.group(1), dot_match.group(2)
        available = _get_available_tables(spec)
        if table not in available:
            allowed_tables = get_allowed_table_names()
            if table not in allowed_tables:
                raise QueryBuildError(f"허용되지 않은 테이블: {table}")
            raise QueryBuildError(f"JOIN되지 않은 테이블: {table} (joins에 추가 필요)")
        cols = get_allowed_columns(table)
        if column not in cols:
            raise QueryBuildError(f"허용되지 않은 컬럼: {table}.{column}")
        return table, column
    cols = get_allowed_columns(spec.table)
    if col not in cols:
        raise QueryBuildError(f"허용되지 않은 컬럼: {spec.table}.{col}")
    return spec.table, col


def _strip_alias(col: str) -> str:
    return _ALIAS_PATTERN.sub("", col).strip()


def _sanitize_spec(spec: QuerySpec) -> QuerySpec:
    updates: dict = {}
    cleaned_cols = [_strip_alias(c) for c in spec.columns]
    if cleaned_cols != spec.columns:
        updates["columns"] = cleaned_cols
    if spec.group_by:
        cleaned_gb = [_strip_alias(c) for c in spec.group_by]
        if cleaned_gb != spec.group_by:
            updates["group_by"] = cleaned_gb
    if spec.order_by:
        cleaned_ob = [
            o.model_copy(update={"column": _strip_alias(o.column)})
            if _strip_alias(o.column) != o.column else o
            for o in spec.order_by
        ]
        if any(a.column != b.column for a, b in zip(cleaned_ob, spec.order_by)):
            updates["order_by"] = cleaned_ob
    if updates:
        spec = spec.model_copy(update=updates)
    return spec


def build_query(spec: QuerySpec, *, tenant_id: int) -> tuple[str, dict]:
    spec = _sanitize_spec(spec)
    if spec.columns == ["*"]:
        cols = get_allowed_columns(spec.table)
        if cols:
            spec = spec.model_copy(update={"columns": sorted(cols)})
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

    def _qualify_expr(col: str) -> str:
        agg_match = _AGG_PATTERN.match(col)
        dt_match = _DATE_TRUNC_PATTERN.match(col)
        if agg_match:
            func, arg = agg_match.group(1).upper(), agg_match.group(2)
            if arg == "*":
                return f'{func}(*) AS "{col}"'
            tbl, c = _resolve_column(arg, spec)
            return f'{func}({tbl}.{c}) AS "{col}"'
        if dt_match:
            interval, column = dt_match.group(1), dt_match.group(2)
            tbl, c = _resolve_column(column, spec)
            return f"DATE_TRUNC('{interval}', {tbl}.{c}) AS \"{col}\""
        tbl, c = _resolve_column(col, spec)
        return f"{tbl}.{c}"

    select_exprs: list[str] = [_qualify_expr(col) for col in spec.columns]

    sql_parts = [f"SELECT {', '.join(select_exprs)}", f"FROM {spec.table}"]

    # tenant_id 격리 JOIN (무조건)
    tenant_joined_tables: set[str] = set()
    if table_meta.tenant_path and table_meta.tenant_path.joins:
        prev_table = spec.table
        for join_table, self_col, join_col in table_meta.tenant_path.joins:
            sql_parts.append(f"JOIN {join_table} ON {prev_table}.{self_col} = {join_table}.{join_col}")
            tenant_joined_tables.add(join_table)
            prev_table = join_table

    # 사용자 지정 JOIN (tenant JOIN과 중복 제거, WHERE 앞에 위치)
    for join in spec.joins:
        if join.table in tenant_joined_tables:
            continue
        sql_parts.append(f"JOIN {join.table} ON {spec.table}.{join.on_self} = {join.table}.{join.on_other}")

    # WHERE — tenant_id 격리
    if table_meta.tenant_path and table_meta.tenant_path.joins:
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

    # 사용자 필터
    for f in spec.filters:
        f_tbl, f_col = _resolve_column(f.column, spec)
        col_ref = f"{f_tbl}.{f_col}"
        if f.operator in (FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL):
            sql_parts.append(f"AND {col_ref} {f.operator.value}")
        elif f.operator == FilterOperator.IN:
            if not isinstance(f.value, list) or not f.value:
                raise QueryBuildError("IN 연산자에는 비어있지 않은 리스트가 필요합니다")
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

    def _qualify_ref(col: str) -> str:
        agg_match = _AGG_PATTERN.match(col)
        dt_match = _DATE_TRUNC_PATTERN.match(col)
        if agg_match:
            func, arg = agg_match.group(1).upper(), agg_match.group(2)
            if arg == "*":
                return f"{func}(*)"
            tbl, c = _resolve_column(arg, spec)
            return f"{func}({tbl}.{c})"
        if dt_match:
            interval, column = dt_match.group(1), dt_match.group(2)
            tbl, c = _resolve_column(column, spec)
            return f"DATE_TRUNC('{interval}', {tbl}.{c})"
        tbl, c = _resolve_column(col, spec)
        return f"{tbl}.{c}"

    # GROUP BY
    if spec.group_by:
        group_cols = ", ".join(_qualify_ref(c) for c in spec.group_by)
        sql_parts.append(f"GROUP BY {group_cols}")

    # ORDER BY
    if spec.order_by:
        order_exprs = [f"{_qualify_ref(o.column)} {o.direction.value}" for o in spec.order_by]
        sql_parts.append(f"ORDER BY {', '.join(order_exprs)}")

    # LIMIT
    limit_placeholder = _next_param(spec.limit)
    sql_parts.append(f"LIMIT {limit_placeholder}")

    return " ".join(sql_parts), params


def _validate_spec(spec: QuerySpec) -> None:
    allowed_tables = get_allowed_table_names()
    if spec.table not in allowed_tables:
        raise QueryBuildError(f"허용되지 않은 테이블: {spec.table}")

    # JOIN 검증을 먼저 수행 (이후 _resolve_column에서 available tables 참조)
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

    for col in spec.columns:
        agg_match = _AGG_PATTERN.match(col)
        dt_match = _DATE_TRUNC_PATTERN.match(col)
        if agg_match:
            func, arg = agg_match.group(1).upper(), agg_match.group(2)
            if func not in ALLOWED_AGGREGATES:
                raise QueryBuildError(f"허용되지 않은 집계 함수: {func}")
            if arg == "*" and func != "COUNT":
                raise QueryBuildError(f"{func}(*)는 허용되지 않습니다. *는 COUNT에서만 사용 가능합니다")
            if arg != "*":
                _resolve_column(arg, spec)
        elif dt_match:
            _validate_date_trunc_interval(dt_match.group(1))
            _resolve_column(dt_match.group(2), spec)
        else:
            _resolve_column(col, spec)

    for f in spec.filters:
        tbl, col = _resolve_column(f.column, spec)
        filterable = get_filterable_columns(tbl)
        if col not in filterable:
            all_cols = get_allowed_columns(tbl)
            if col in all_cols:
                raise QueryBuildError(f"필터링할 수 없는 컬럼: {tbl}.{col}")
            raise QueryBuildError(f"허용되지 않은 컬럼: {tbl}.{col}")

    if spec.order_by:
        for o in spec.order_by:
            agg_match = _AGG_PATTERN.match(o.column)
            dt_match = _DATE_TRUNC_PATTERN.match(o.column)
            if agg_match:
                arg = agg_match.group(2)
                if arg != "*":
                    _resolve_column(arg, spec)
            elif dt_match:
                _validate_date_trunc_interval(dt_match.group(1))
                _resolve_column(dt_match.group(2), spec)
            else:
                _resolve_column(o.column, spec)

    if spec.group_by:
        for col in spec.group_by:
            dt_match = _DATE_TRUNC_PATTERN.match(col)
            if dt_match:
                _validate_date_trunc_interval(dt_match.group(1))
                _resolve_column(dt_match.group(2), spec)
            else:
                _resolve_column(col, spec)
