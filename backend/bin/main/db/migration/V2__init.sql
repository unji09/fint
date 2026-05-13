-- =====================================================
-- F!NT B2B Sales CRM - Initial Schema
-- =====================================================

-- 테넌트 (서비스 계약 단위 - 회사)
CREATE TABLE tenants (
                         tenant_id       BIGSERIAL       PRIMARY KEY,
                         name            VARCHAR(100)    NOT NULL,
                         company_code    VARCHAR(20)     NOT NULL UNIQUE,
                         biz_no          VARCHAR(20)     NULL,
                         plan            VARCHAR(20)     NULL DEFAULT 'BASIC',
                         created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                         updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                         is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 팀
CREATE TABLE teams (
                       team_id         BIGSERIAL       PRIMARY KEY,
                       tenant_id       BIGINT          NOT NULL REFERENCES tenants(tenant_id),
                       name            VARCHAR(50)     NOT NULL,
                       created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 사원
CREATE TABLE users (
                       user_id             BIGSERIAL       PRIMARY KEY,
                       team_id             BIGINT          NULL     REFERENCES teams(team_id),
                       tenant_id           BIGINT          NOT NULL REFERENCES tenants(tenant_id),
                       role                VARCHAR(20)     NOT NULL,
                       email               VARCHAR(200)    NULL,
                       emp_no              VARCHAR(30)     NULL,
                       name                VARCHAR(50)     NOT NULL,
                       password_hash       VARCHAR(200)    NOT NULL,
                       email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
                       notification_prefs  JSONB           NULL,
                       login_fail_count    INT             NOT NULL DEFAULT 0,
                       created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 고객사
CREATE TABLE accounts (
                          account_id      BIGSERIAL       PRIMARY KEY,
                          user_id         BIGINT          NOT NULL REFERENCES users(user_id),
                          name            VARCHAR(200)    NOT NULL,
                          industry        VARCHAR(100)    NOT NULL,
                          biz_no          VARCHAR(20)     NULL,
                          created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                          updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                          is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 고객사 담당자
CREATE TABLE contacts (
                          contact_id      BIGSERIAL       PRIMARY KEY,
                          account_id      BIGINT          NOT NULL REFERENCES accounts(account_id),
                          name            VARCHAR(50)     NOT NULL,
                          title           VARCHAR(100)    NULL,
                          phone           VARCHAR(30)     NULL,
                          email           VARCHAR(200)    NULL,
                          personality     TEXT            NULL,
                          created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                          updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                          is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 고객사 외부 정보 (뉴스, DART)
CREATE TABLE account_external_info (
                                       account_external_info_id    BIGSERIAL       PRIMARY KEY,
                                       account_id                  BIGINT          NOT NULL REFERENCES accounts(account_id),
                                       source                      VARCHAR(20)     NOT NULL, -- NEWS, DART
                                       title                       VARCHAR(500)    NOT NULL,
                                       content                     TEXT            NOT NULL,
                                       url                         VARCHAR(2000)   NULL,
                                       occurred_at                 TIMESTAMPTZ     NOT NULL
);

-- 고객사 온도 이력
CREATE TABLE temperature_history (
                                     temperature_history_id  BIGSERIAL       PRIMARY KEY,
                                     account_id              BIGINT          NOT NULL REFERENCES accounts(account_id),
                                     temperature             INT             NOT NULL,
                                     reason                  TEXT            NOT NULL,
                                     created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 파이프라인 스테이지
CREATE TABLE pipeline_stages (
                                 pipeline_stage_id   BIGSERIAL       PRIMARY KEY,
                                 tenant_id           BIGINT          NOT NULL REFERENCES tenants(tenant_id),
                                 name                VARCHAR(50)     NOT NULL,
                                 sort_order          INT             NOT NULL,
                                 created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 영업건
CREATE TABLE deals (
                       deal_id             BIGSERIAL       PRIMARY KEY,
                       account_id          BIGINT          NOT NULL REFERENCES accounts(account_id),
                       team_id             BIGINT          NULL     REFERENCES teams(team_id),
                       title               VARCHAR(200)    NOT NULL,
                       expected_close      DATE            NULL,
                       amount              NUMERIC(15,2)   NULL,
                       probability         SMALLINT        NULL,
                       won_at              TIMESTAMPTZ     NULL,
                       lost_at             TIMESTAMPTZ     NULL,
                       lost_reason         TEXT            NULL,
                       current_pipeline    VARCHAR(50)     NULL,
                       created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 활동 (미팅, 전화, 업무, 이메일)
CREATE TABLE activities (
                            activity_id         BIGSERIAL       PRIMARY KEY,
                            deal_id             BIGINT          NULL REFERENCES deals(deal_id),
                            pipeline_stage_id   BIGINT          NULL REFERENCES pipeline_stages(pipeline_stage_id),
                            type                VARCHAR(20)     NOT NULL, -- 미팅, 전화, 업무, 이메일
                            device_event_id     VARCHAR(200)    NULL,     -- 외부 캘린더 동기화용
                            title               VARCHAR(300)    NOT NULL,
                            start_at            TIMESTAMPTZ     NOT NULL,
                            end_at              TIMESTAMPTZ     NOT NULL,
                            attendees           JSON            NULL,
                            transcript          JSON            NULL,
                            summary             JSON            NULL,     -- 핵심 논의, 고객 니즈, 합의 사항
                            created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                            updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 딜-담당자 연결
CREATE TABLE deal_contacts (
                               deal_contact_id BIGSERIAL       PRIMARY KEY,
                               deal_id         BIGINT          NOT NULL REFERENCES deals(deal_id),
                               contact_id      BIGINT          NOT NULL REFERENCES contacts(contact_id),
                               user_id         BIGINT          NOT NULL REFERENCES users(user_id),
                               created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- AI 제안
CREATE TABLE ai_suggestions (
                                ai_suggestion_id    BIGSERIAL       PRIMARY KEY,
                                account_id          BIGINT          NOT NULL REFERENCES accounts(account_id),
                                title               VARCHAR(200)    NOT NULL,
                                content             TEXT            NOT NULL,
                                related_type        VARCHAR(50)     NOT NULL, -- meeting, account
                                is_read             BOOLEAN         NOT NULL DEFAULT FALSE,
                                created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                reason              JSON            NOT NULL
);

-- 이메일 OAuth 토큰
CREATE TABLE email_oauth_tokens (
                                    email_oauth_id      BIGSERIAL       PRIMARY KEY,
                                    user_id             BIGINT          NOT NULL REFERENCES users(user_id),
                                    provider            VARCHAR(20)     NOT NULL,
                                    email_address       VARCHAR(200)    NOT NULL,
                                    access_token_enc    TEXT            NOT NULL,
                                    refresh_token_enc   TEXT            NOT NULL,
                                    expires_at          TIMESTAMPTZ     NOT NULL,
                                    last_sync_at        TIMESTAMPTZ     NULL,
                                    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 파일
CREATE TABLE files (
                       file_id         BIGSERIAL       PRIMARY KEY,
                       user_id         BIGINT          NOT NULL REFERENCES users(user_id),
                       original_name   VARCHAR(300)    NULL,
                       mime            VARCHAR(100)    NOT NULL,
                       size            BIGINT          NOT NULL,
                       created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 대시보드
CREATE TABLE dashboards (
                            dashboard_id        BIGSERIAL       PRIMARY KEY,
                            owner_user_id       BIGINT          NOT NULL REFERENCES users(user_id),
                            title               VARCHAR(100)    NOT NULL,
                            last_accessed_at    TIMESTAMPTZ     NULL,
                            thumnail_url        VARCHAR(300)    NULL,
                            created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                            updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 대시보드 위젯
CREATE TABLE dashboard_widgets (
                                   dashboard_widget_id BIGSERIAL       PRIMARY KEY,
                                   dashboard_id        BIGINT          NULL REFERENCES dashboards(dashboard_id),
                                   widget_type         VARCHAR(30)     NOT NULL,
                                   title               VARCHAR(100)    NULL,
                                   config              JSONB           NOT NULL,
                                   position            JSONB           NOT NULL,
                                   source_query        TEXT            NULL,
                                   created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                   updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 대시보드 쿼리
CREATE TABLE dashboard_queries (
                                   dashboard_query_id  BIGSERIAL       PRIMARY KEY,
                                   dashboard_widget_id BIGINT          NOT NULL REFERENCES dashboard_widgets(dashboard_widget_id),
                                   dashboard_id        BIGINT          NOT NULL REFERENCES dashboards(dashboard_id),
                                   input_text          TEXT            NOT NULL,
                                   result              JSONB           NOT NULL,
                                   completed_at        TIMESTAMPTZ     NOT NULL,
                                   created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 대시보드 템플릿
CREATE TABLE dashboard_templates (
                                     dashboard_template_id   BIGSERIAL       PRIMARY KEY,
                                     widget_type             VARCHAR(30)     NOT NULL,
                                     title                   VARCHAR(100)    NULL,
                                     config                  JSONB           NOT NULL,
                                     position                JSONB           NOT NULL,
                                     created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
