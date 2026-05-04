-- =====================================================
-- F!NT B2B Sales CRM - V5 활동 소유자
-- user_id 컬럼 추가 (REQ-ACT 활동 소유자, tenant 격리 단일 경로)
-- 주의: user_id 는 NOT NULL FK. 본 마이그레이션 적용 전 activities 에
--       기존 데이터가 있다면 백필 또는 TRUNCATE 가 선행되어야 한다.
-- =====================================================

ALTER TABLE activities
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(user_id);

CREATE INDEX idx_activities_user_id ON activities(user_id);
