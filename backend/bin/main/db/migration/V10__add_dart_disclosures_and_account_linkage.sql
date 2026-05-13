-- =====================================================
-- V10: news_articles 스키마 보정 + DART 공시 테이블 + Account 연결 테이블
--
-- 0) news_articles: 네이버 뉴스 API 대응 (NOT NULL 완화, original_link 추가, URL dedup)
-- 1) dart_disclosures: DART 전자공시 원본 데이터 풀
-- 2) account_news_articles: Account ↔ news_articles M:N
-- 3) account_dart_disclosures: Account ↔ dart_disclosures M:N
--
-- account_external_info 는 변경하지 않음 (하위 호환)
-- =====================================================

-- ===== 0. news_articles 스키마 보정 =====
-- 네이버 뉴스 API는 publisher/article 을 제공하지 않음
ALTER TABLE news_articles ALTER COLUMN publisher DROP NOT NULL;
ALTER TABLE news_articles ALTER COLUMN article DROP NOT NULL;

-- 네이버 API의 originallink (원문 URL) 저장용
ALTER TABLE news_articles ADD COLUMN original_link VARCHAR(2000) NULL;

-- URL 기반 중복 방지 (네이버 뉴스용, 기존 idx_news_dedup 은 CSV 배치용으로 유지)
CREATE UNIQUE INDEX uk_news_link ON news_articles (link) WHERE link IS NOT NULL;


-- ===== 1. dart_disclosures (DART 전자공시 원본 풀) =====
-- 전 테넌트 공유 읽기 전용 공개 데이터 → tenant_id 없음
CREATE TABLE dart_disclosures (
    dart_disclosure_id  BIGSERIAL       PRIMARY KEY,

    -- DART OpenAPI 원본 필드
    corp_code           VARCHAR(8)      NOT NULL,
    corp_name           VARCHAR(200)    NOT NULL,
    stock_code          VARCHAR(6)      NULL,
    corp_cls            VARCHAR(1)      NULL,
    report_nm           VARCHAR(500)    NOT NULL,
    rcept_no            VARCHAR(14)     NOT NULL,
    flr_nm              VARCHAR(200)    NULL,
    rcept_dt            VARCHAR(8)      NOT NULL,
    rm                  VARCHAR(500)    NULL,

    -- AI 처리 필드
    content             TEXT            NULL,
    content_summary     TEXT            NULL,
    title_embedding     vector(384)     NULL,
    summary_embedding   vector(384)     NULL,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

ALTER TABLE dart_disclosures
    ADD CONSTRAINT dart_disclosures_corp_cls_check
        CHECK (corp_cls IS NULL OR corp_cls IN ('Y', 'K', 'N', 'E'));

CREATE UNIQUE INDEX uk_dart_disclosures_rcept_no
    ON dart_disclosures (rcept_no);

CREATE INDEX idx_dart_disclosures_corp_code
    ON dart_disclosures (corp_code);

CREATE INDEX idx_dart_disclosures_rcept_dt
    ON dart_disclosures (rcept_dt DESC);

CREATE INDEX idx_dart_disclosures_corp_rcept_dt
    ON dart_disclosures (corp_code, rcept_dt DESC);


-- ===== 2. account_news_articles (Account ↔ news_articles M:N) =====
CREATE TABLE account_news_articles (
    account_news_article_id  BIGSERIAL       PRIMARY KEY,
    account_id               BIGINT          NOT NULL REFERENCES accounts(account_id),
    news_article_id          BIGINT          NOT NULL REFERENCES news_articles(news_article_id),
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, news_article_id)
);

CREATE INDEX idx_ana_account_id
    ON account_news_articles (account_id);

CREATE INDEX idx_ana_news_article_id
    ON account_news_articles (news_article_id);


-- ===== 3. account_dart_disclosures (Account ↔ dart_disclosures M:N) =====
CREATE TABLE account_dart_disclosures (
    account_dart_disclosure_id  BIGSERIAL       PRIMARY KEY,
    account_id                  BIGINT          NOT NULL REFERENCES accounts(account_id),
    dart_disclosure_id          BIGINT          NOT NULL REFERENCES dart_disclosures(dart_disclosure_id),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, dart_disclosure_id)
);

CREATE INDEX idx_add_account_id
    ON account_dart_disclosures (account_id);

CREATE INDEX idx_add_dart_disclosure_id
    ON account_dart_disclosures (dart_disclosure_id);
