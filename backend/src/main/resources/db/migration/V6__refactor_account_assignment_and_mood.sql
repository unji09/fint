-- V6: Account 도메인 스키마 재설계
-- 1) account_user_assignment 테이블 신설 (account ↔ user 다대다)
-- 2) accounts.user_id 컬럼 제거 (책임자 정보를 assignment 로 이전)
-- 3) temperature_history.temperature (INT 0~100) → mood (VARCHAR enum 5단계)

-- ===== 1. account_user_assignment 테이블 =====
CREATE TABLE account_user_assignment (
    assignment_id  BIGSERIAL    PRIMARY KEY,
    account_id     BIGINT       NOT NULL REFERENCES accounts(account_id),
    user_id        BIGINT       NOT NULL REFERENCES users(user_id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, user_id)
);

CREATE INDEX idx_aua_account_id ON account_user_assignment(account_id);
CREATE INDEX idx_aua_user_id    ON account_user_assignment(user_id);

-- ===== 2. accounts.user_id 데이터를 assignment 로 backfill =====
INSERT INTO account_user_assignment (account_id, user_id)
SELECT account_id, user_id
FROM accounts
WHERE user_id IS NOT NULL;

-- accounts.user_id 컬럼 제거
ALTER TABLE accounts DROP COLUMN user_id;

-- ===== 3. temperature_history.temperature → mood =====
ALTER TABLE temperature_history ADD COLUMN mood VARCHAR(20);

UPDATE temperature_history SET mood =
    CASE
        WHEN temperature >= 80 THEN 'RAINBOW'
        WHEN temperature >= 60 THEN 'SUNNY'
        WHEN temperature >= 40 THEN 'CLOUDY'
        WHEN temperature >= 20 THEN 'RAINY'
        ELSE 'THUNDER'
    END;

ALTER TABLE temperature_history ALTER COLUMN mood SET NOT NULL;
ALTER TABLE temperature_history DROP COLUMN temperature;
