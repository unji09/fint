import pytest

from app.dashboard.query_builder import QueryBuildError, build_query
from app.schemas.dashboard import (
    FilterCondition,
    FilterOperator,
    JoinSpec,
    OrderDirection,
    OrderSpec,
    QuerySpec,
)


class TestBasicQuery:
    def test_simple_select(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name", "industry"],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "SELECT" in sql
        assert "accounts" in sql
        assert "name" in sql
        assert "industry" in sql
        assert 1 in params.values()

    def test_tenant_id_always_injected(self):
        spec = QuerySpec(table="accounts", columns=["name"])
        sql, params = build_query(spec, tenant_id=42)

        assert "tenant_id" in sql or "user_id" in sql
        assert 42 in params.values()

    def test_limit_applied(self):
        spec = QuerySpec(table="accounts", columns=["name"], limit=10)
        sql, params = build_query(spec, tenant_id=1)

        assert "LIMIT" in sql
        assert 10 in params.values()

    def test_default_limit(self):
        spec = QuerySpec(table="accounts", columns=["name"])
        sql, params = build_query(spec, tenant_id=1)

        assert "LIMIT" in sql


class TestFilters:
    def test_equality_filter(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            filters=[
                FilterCondition(column="current_pipeline", operator=FilterOperator.EQ, value="NEGOTIATION"),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "current_pipeline" in sql
        assert "NEGOTIATION" in params.values()

    def test_comparison_filter(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            filters=[
                FilterCondition(column="amount", operator=FilterOperator.GTE, value=1000000),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "amount" in sql
        assert 1000000 in params.values()

    def test_like_filter(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name"],
            filters=[
                FilterCondition(column="name", operator=FilterOperator.LIKE, value="%삼성%"),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "LIKE" in sql
        assert "%삼성%" in params.values()

    def test_in_filter(self):
        spec = QuerySpec(
            table="activities",
            columns=["title", "type"],
            filters=[
                FilterCondition(column="type", operator=FilterOperator.IN, value=["미팅", "전화"]),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "IN" in sql

    def test_in_empty_list_raises(self):
        spec = QuerySpec(
            table="activities",
            columns=["title"],
            filters=[
                FilterCondition(column="type", operator=FilterOperator.IN, value=[]),
            ],
        )
        with pytest.raises(QueryBuildError, match="비어있지 않은 리스트"):
            build_query(spec, tenant_id=1)

    def test_between_filter(self):
        spec = QuerySpec(
            table="deals",
            columns=["title"],
            filters=[
                FilterCondition(
                    column="amount",
                    operator=FilterOperator.BETWEEN,
                    value=[100000, 500000],
                ),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "BETWEEN" in sql

    def test_is_null_filter(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            filters=[
                FilterCondition(column="won_at", operator=FilterOperator.IS_NULL),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "IS NULL" in sql
        assert "won_at" in sql

    def test_is_not_null_filter(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            filters=[
                FilterCondition(column="won_at", operator=FilterOperator.IS_NOT_NULL),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "IS NOT NULL" in sql
        assert "won_at" in sql

    def test_is_null_does_not_add_parameter(self):
        spec = QuerySpec(
            table="deals",
            columns=["title"],
            filters=[
                FilterCondition(column="won_at", operator=FilterOperator.IS_NULL),
            ],
        )
        _, params = build_query(spec, tenant_id=1)

        param_values = list(params.values())
        assert None not in param_values


class TestWildcardExpansion:
    def test_star_expands_to_all_columns(self):
        spec = QuerySpec(table="accounts", columns=["*"])
        sql, _ = build_query(spec, tenant_id=1)

        assert "name" in sql
        assert "industry" in sql
        assert "account_id" in sql

    def test_star_expansion_produces_valid_sql(self):
        spec = QuerySpec(table="accounts", columns=["*"])
        sql, params = build_query(spec, tenant_id=1)

        assert "SELECT" in sql
        assert "*" not in sql.split("FROM")[0]


class TestDateTrunc:
    def test_date_trunc_in_columns(self):
        spec = QuerySpec(
            table="deals",
            columns=["DATE_TRUNC('month', won_at)", "SUM(amount)"],
            group_by=["DATE_TRUNC('month', won_at)"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "DATE_TRUNC('month', deals.won_at)" in sql

    def test_date_trunc_in_group_by(self):
        spec = QuerySpec(
            table="deals",
            columns=["DATE_TRUNC('month', won_at)", "COUNT(*)"],
            group_by=["DATE_TRUNC('month', won_at)"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "GROUP BY DATE_TRUNC('month', deals.won_at)" in sql

    def test_date_trunc_invalid_column_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["DATE_TRUNC('month', fake_col)"],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)

    def test_date_trunc_has_alias(self):
        spec = QuerySpec(
            table="deals",
            columns=["DATE_TRUNC('month', won_at)"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "AS \"DATE_TRUNC('month', won_at)\"" in sql


class TestOrderAndGroupBy:
    def test_order_by(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            order_by=[OrderSpec(column="amount", direction=OrderDirection.DESC)],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "ORDER BY" in sql
        assert "DESC" in sql

    def test_group_by(self):
        spec = QuerySpec(
            table="deals",
            columns=["current_pipeline", "amount"],
            group_by=["current_pipeline"],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "GROUP BY" in sql
        assert "current_pipeline" in sql


class TestAggregates:
    def test_count_star(self):
        spec = QuerySpec(
            table="deals",
            columns=["current_pipeline", "COUNT(*)"],
            group_by=["current_pipeline"],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "COUNT(*)" in sql
        assert "GROUP BY" in sql

    def test_sum_column(self):
        spec = QuerySpec(
            table="deals",
            columns=["current_pipeline", "SUM(amount)"],
            group_by=["current_pipeline"],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "SUM(deals.amount)" in sql

    def test_avg_column(self):
        spec = QuerySpec(
            table="deals",
            columns=["current_pipeline", "AVG(amount)"],
            group_by=["current_pipeline"],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "AVG(deals.amount)" in sql

    def test_min_max_column(self):
        spec = QuerySpec(
            table="deals",
            columns=["MIN(amount)", "MAX(amount)"],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "MIN(deals.amount)" in sql
        assert "MAX(deals.amount)" in sql

    def test_aggregate_invalid_column_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["SUM(fake_col)"],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)

    def test_aggregate_alias_matches_column_spec(self):
        spec = QuerySpec(
            table="deals",
            columns=["current_pipeline", "COUNT(*)"],
            group_by=["current_pipeline"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert 'AS "COUNT(*)"' in sql

    def test_sum_alias_matches_column_spec(self):
        spec = QuerySpec(
            table="deals",
            columns=["current_pipeline", "SUM(amount)"],
            group_by=["current_pipeline"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert 'AS "SUM(amount)"' in sql

    def test_sum_star_raises(self):
        spec = QuerySpec(table="deals", columns=["SUM(*)"])
        with pytest.raises(QueryBuildError, match="SUM.*허용되지 않습니다"):
            build_query(spec, tenant_id=1)

    def test_avg_star_raises(self):
        spec = QuerySpec(table="deals", columns=["AVG(*)"])
        with pytest.raises(QueryBuildError, match="AVG.*허용되지 않습니다"):
            build_query(spec, tenant_id=1)

    def test_disallowed_aggregate_function_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["GROUP_CONCAT(title)"],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)


class TestJoins:
    def test_allowed_join(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "JOIN" in sql
        assert "accounts" in sql

    def test_user_join_comes_before_where(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name"],
            joins=[JoinSpec(table="deals", on_self="account_id", on_other="account_id")],
        )
        sql, _ = build_query(spec, tenant_id=1)

        join_pos = sql.index("JOIN deals")
        where_pos = sql.index("WHERE")
        assert join_pos < where_pos

    def test_activities_join_pipeline_stages(self):
        spec = QuerySpec(
            table="activities",
            columns=["title", "type"],
            joins=[JoinSpec(table="pipeline_stages", on_self="pipeline_stage_id", on_other="pipeline_stage_id")],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "JOIN pipeline_stages" in sql
        assert "pipeline_stage_id" in sql

    def test_activities_join_comes_before_where(self):
        spec = QuerySpec(
            table="activities",
            columns=["title"],
            joins=[JoinSpec(table="pipeline_stages", on_self="pipeline_stage_id", on_other="pipeline_stage_id")],
        )
        sql, params = build_query(spec, tenant_id=1)

        join_pos = sql.index("JOIN pipeline_stages")
        where_pos = sql.index("WHERE")
        assert join_pos < where_pos

    def test_tenant_path_join_dedup(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert sql.count("JOIN accounts") == 1
        assert "JOIN account_user_assignment" in sql
        assert "JOIN users" in sql
        assert "users.tenant_id" in sql

    def test_disallowed_join_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["title"],
            joins=[JoinSpec(table="pipeline_stages", on_self="id", on_other="id")],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 JOIN"):
            build_query(spec, tenant_id=1)

    def test_join_wrong_columns_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["title"],
            joins=[JoinSpec(table="accounts", on_self="deal_id", on_other="deal_id")],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 JOIN 컬럼"):
            build_query(spec, tenant_id=1)


class TestSecurity:
    def test_disallowed_table_raises(self):
        spec = QuerySpec(table="users", columns=["password_hash"])
        with pytest.raises(QueryBuildError, match="허용되지 않은 테이블"):
            build_query(spec, tenant_id=1)

    def test_disallowed_column_raises(self):
        spec = QuerySpec(table="accounts", columns=["nonexistent_column"])
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)

    def test_disallowed_filter_column_raises(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name"],
            filters=[FilterCondition(column="fake_col", operator=FilterOperator.EQ, value="x")],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)

    def test_disallowed_order_column_raises(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name"],
            order_by=[OrderSpec(column="fake_col")],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)

    def test_disallowed_group_by_column_raises(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name"],
            group_by=["fake_col"],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)

    def test_sql_injection_in_value_is_parameterized(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name"],
            filters=[
                FilterCondition(
                    column="name",
                    operator=FilterOperator.EQ,
                    value="'; DROP TABLE accounts;--",
                ),
            ],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "DROP" not in sql
        assert "'; DROP TABLE accounts;--" in params.values()

    def test_jsonb_column_filter_raises(self):
        spec = QuerySpec(
            table="activities",
            columns=["title"],
            filters=[
                FilterCondition(column="attendees", operator=FilterOperator.EQ, value="test"),
            ],
        )
        with pytest.raises(QueryBuildError, match="필터링할 수 없는 컬럼"):
            build_query(spec, tenant_id=1)

    def test_jsonb_column_select_allowed(self):
        spec = QuerySpec(
            table="activities",
            columns=["title", "summary"],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "summary" in sql


class TestTenantIsolation:
    def test_accounts_tenant_filter_via_users_join(self):
        spec = QuerySpec(table="accounts", columns=["name"])
        sql, params = build_query(spec, tenant_id=7)

        assert 7 in params.values()

    def test_deals_tenant_filter_via_chain(self):
        spec = QuerySpec(table="deals", columns=["title"])
        sql, params = build_query(spec, tenant_id=7)

        assert 7 in params.values()

    def test_pipeline_stages_direct_tenant_filter(self):
        spec = QuerySpec(table="pipeline_stages", columns=["name"])
        sql, params = build_query(spec, tenant_id=7)

        assert "tenant_id" in sql
        assert 7 in params.values()

    def test_activities_tenant_filter_via_user(self):
        spec = QuerySpec(table="activities", columns=["title"])
        sql, params = build_query(spec, tenant_id=7)

        assert "JOIN users" in sql
        assert "users.tenant_id" in sql
        assert 7 in params.values()
        assert "JOIN deals" not in sql

    def test_soft_delete_filter_applied(self):
        spec = QuerySpec(table="accounts", columns=["name"])
        sql, params = build_query(spec, tenant_id=1)

        assert "is_deleted" in sql

    def test_soft_delete_not_applied_for_activities(self):
        spec = QuerySpec(table="activities", columns=["title"])
        sql, params = build_query(spec, tenant_id=1)

        assert "is_deleted" not in sql

    def test_accounts_tenant_via_account_user_assignment(self):
        spec = QuerySpec(table="accounts", columns=["name"])
        sql, _ = build_query(spec, tenant_id=1)

        assert "JOIN account_user_assignment" in sql
        assert "JOIN users" in sql
        assert "users.tenant_id" in sql

    def test_deals_tenant_via_accounts_chain(self):
        spec = QuerySpec(table="deals", columns=["title"])
        sql, _ = build_query(spec, tenant_id=1)

        assert "JOIN accounts" in sql
        assert "JOIN account_user_assignment" in sql
        assert "JOIN users" in sql
        assert "users.tenant_id" in sql

    def test_contacts_tenant_via_accounts_chain(self):
        spec = QuerySpec(table="contacts", columns=["name"])
        sql, _ = build_query(spec, tenant_id=1)

        assert "JOIN accounts" in sql
        assert "JOIN account_user_assignment" in sql
        assert "JOIN users" in sql
        assert "users.tenant_id" in sql


class TestCrossTableColumns:
    def test_filter_on_joined_table_column(self):
        spec = QuerySpec(
            table="deals",
            columns=["title", "amount"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
            filters=[FilterCondition(column="accounts.name", operator=FilterOperator.LIKE, value="%삼성%")],
        )
        sql, params = build_query(spec, tenant_id=1)

        assert "accounts.name LIKE" in sql
        assert "%삼성%" in params.values()

    def test_select_joined_table_column(self):
        spec = QuerySpec(
            table="deals",
            columns=["accounts.name", "amount"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "accounts.name" in sql

    def test_group_by_joined_table_column(self):
        spec = QuerySpec(
            table="deals",
            columns=["accounts.name", "SUM(amount)"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
            group_by=["accounts.name"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "GROUP BY accounts.name" in sql

    def test_order_by_joined_table_column(self):
        spec = QuerySpec(
            table="deals",
            columns=["accounts.name", "amount"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
            order_by=[OrderSpec(column="accounts.name")],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "ORDER BY accounts.name" in sql

    def test_main_table_dot_notation(self):
        spec = QuerySpec(
            table="deals",
            columns=["deals.title", "amount"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "deals.title" in sql

    def test_aggregate_on_joined_table(self):
        spec = QuerySpec(
            table="activities",
            columns=["deals.title", "COUNT(*)"],
            joins=[JoinSpec(table="deals", on_self="deal_id", on_other="deal_id")],
            group_by=["deals.title"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "deals.title" in sql
        assert "GROUP BY deals.title" in sql

    def test_filter_on_non_filterable_joined_column_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["title"],
            joins=[JoinSpec(table="activities", on_self="deal_id", on_other="deal_id")],
            filters=[FilterCondition(column="activities.attendees", operator=FilterOperator.EQ, value="test")],
        )
        with pytest.raises(QueryBuildError, match="필터링할 수 없는 컬럼"):
            build_query(spec, tenant_id=1)

    def test_unjoined_table_column_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["contacts.name", "amount"],
        )
        with pytest.raises(QueryBuildError):
            build_query(spec, tenant_id=1)

    def test_invalid_table_in_dot_notation_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["users.password_hash"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
        )
        with pytest.raises(QueryBuildError):
            build_query(spec, tenant_id=1)

    def test_invalid_column_in_dot_notation_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["accounts.fake_col"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
        )
        with pytest.raises(QueryBuildError, match="허용되지 않은 컬럼"):
            build_query(spec, tenant_id=1)

    def test_filter_on_unjoined_table_raises(self):
        spec = QuerySpec(
            table="deals",
            columns=["title"],
            filters=[FilterCondition(column="contacts.name", operator=FilterOperator.EQ, value="test")],
        )
        with pytest.raises(QueryBuildError):
            build_query(spec, tenant_id=1)


class TestAliasSanitization:
    def test_aggregate_with_alias_stripped(self):
        spec = QuerySpec(
            table="deals",
            columns=["SUM(amount) as total_amount"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "SUM(deals.amount)" in sql

    def test_date_trunc_with_alias_stripped(self):
        spec = QuerySpec(
            table="deals",
            columns=["DATE_TRUNC('month', created_at) AS month"],
            group_by=["DATE_TRUNC('month', created_at) AS month"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "DATE_TRUNC('month', deals.created_at)" in sql
        assert "GROUP BY DATE_TRUNC('month', deals.created_at)" in sql

    def test_plain_column_without_alias_unchanged(self):
        spec = QuerySpec(
            table="accounts",
            columns=["name", "industry"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "accounts.name" in sql
        assert "accounts.industry" in sql

    def test_mixed_alias_and_plain_columns(self):
        spec = QuerySpec(
            table="deals",
            columns=["accounts.name", "SUM(amount) AS total"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
            group_by=["accounts.name"],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "accounts.name" in sql
        assert "SUM(deals.amount)" in sql


class TestOrderByExpressions:
    def test_order_by_date_trunc(self):
        spec = QuerySpec(
            table="deals",
            columns=["DATE_TRUNC('month', created_at)", "COUNT(*)"],
            group_by=["DATE_TRUNC('month', created_at)"],
            order_by=[OrderSpec(column="DATE_TRUNC('month', created_at)")],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "ORDER BY DATE_TRUNC('month', deals.created_at)" in sql

    def test_order_by_aggregate(self):
        spec = QuerySpec(
            table="deals",
            columns=["accounts.name", "SUM(amount)"],
            joins=[JoinSpec(table="accounts", on_self="account_id", on_other="account_id")],
            group_by=["accounts.name"],
            order_by=[OrderSpec(column="SUM(amount)", direction=OrderDirection.DESC)],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "ORDER BY SUM(deals.amount) DESC" in sql

    def test_order_by_with_alias_stripped(self):
        spec = QuerySpec(
            table="deals",
            columns=["DATE_TRUNC('month', created_at) AS month", "COUNT(*)"],
            group_by=["DATE_TRUNC('month', created_at) AS month"],
            order_by=[OrderSpec(column="DATE_TRUNC('month', created_at) AS month")],
        )
        sql, _ = build_query(spec, tenant_id=1)

        assert "ORDER BY DATE_TRUNC('month', deals.created_at)" in sql
