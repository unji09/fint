-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 뉴스 기사 테이블 (전 테넌트 공유 읽기 전용 데이터)
-- tenant_id 없음: 공개 뉴스 데이터로 테넌트 격리 불필요
CREATE TABLE news_articles (
    news_article_id   BIGSERIAL       PRIMARY KEY,
    publisher         VARCHAR(100)    NOT NULL,
    title             VARCHAR(500)    NOT NULL,
    link              VARCHAR(2000)   NULL,
    published_at      TIMESTAMPTZ     NOT NULL,
    category          VARCHAR(50)     NULL,
    reporter          VARCHAR(100)    NULL,
    article           TEXT            NOT NULL,
    content_summary   TEXT            NULL,
    title_embedding   vector(384)     NULL,
    summary_embedding vector(384)     NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_news_published ON news_articles (published_at);
CREATE INDEX idx_news_publisher ON news_articles (publisher);
CREATE UNIQUE INDEX idx_news_dedup ON news_articles (publisher, title, published_at);
