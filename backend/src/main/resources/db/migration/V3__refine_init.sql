-- =====================================================
-- F!NT B2B Sales CRM - V2 보정 마이그레이션
-- =====================================================

-- 1. JSON → JSONB 통일
ALTER TABLE activities
    ALTER COLUMN attendees  TYPE JSONB USING attendees::jsonb,
    ALTER COLUMN transcript TYPE JSONB USING transcript::jsonb,
    ALTER COLUMN summary    TYPE JSONB USING summary::jsonb;

ALTER TABLE ai_suggestions
    ALTER COLUMN reason TYPE JSONB USING reason::jsonb;


-- 2. thumnail_url 오타 수정
ALTER TABLE dashboards
    RENAME COLUMN thumnail_url TO thumbnail_url;


-- 3. ai_suggestions.pipeline_stage_id 추가
ALTER TABLE ai_suggestions
    ADD COLUMN pipeline_stage_id BIGINT NOT NULL
        REFERENCES pipeline_stages(pipeline_stage_id);


-- 4. dashboard_widgets ↔ dashboard_queries 관계 정정 (위젯 → 쿼리)
ALTER TABLE dashboard_queries
    DROP COLUMN dashboard_widget_id;

ALTER TABLE dashboard_widgets
    ADD COLUMN dashboard_query_id BIGINT NOT NULL
        REFERENCES dashboard_queries(dashboard_query_id);


-- 5. 복합 unique 제약
CREATE UNIQUE INDEX uk_users_tenant_email
    ON users(tenant_id, email)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_email_oauth_user_provider_email
    ON email_oauth_tokens(user_id, provider, email_address);

CREATE UNIQUE INDEX uk_pipeline_stages_tenant_name
    ON pipeline_stages(tenant_id, name);

CREATE UNIQUE INDEX uk_pipeline_stages_tenant_sort
    ON pipeline_stages(tenant_id, sort_order);


-- 6. 자주 쓰는 인덱스
-- FK 컬럼 인덱스
CREATE INDEX idx_accounts_user
    ON accounts(user_id) WHERE is_deleted = FALSE;

CREATE INDEX idx_contacts_account
    ON contacts(account_id) WHERE is_deleted = FALSE;

CREATE INDEX idx_deals_account
    ON deals(account_id) WHERE is_deleted = FALSE;

CREATE INDEX idx_deals_team
    ON deals(team_id) WHERE is_deleted = FALSE;

CREATE INDEX idx_dashboards_owner
    ON dashboards(owner_user_id);

-- 시계열 조회 패턴 인덱스 (최신순 조회)
CREATE INDEX idx_activities_deal_start
    ON activities(deal_id, start_at DESC);

CREATE INDEX idx_temperature_history_account_created
    ON temperature_history(account_id, created_at DESC);

CREATE INDEX idx_account_external_info_account_occurred
    ON account_external_info(account_id, occurred_at DESC);

CREATE INDEX idx_ai_suggestions_account_unread
    ON ai_suggestions(account_id, is_read, created_at DESC);
