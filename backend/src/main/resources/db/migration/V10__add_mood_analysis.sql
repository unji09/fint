-- =====================================================
-- F!NT B2B Sales CRM - V10 날씨 분석 컬럼 추가
-- 1) activities.mood_status       : 날씨 분석 처리 상태
-- 2) temperature_history.mood_score  : 날씨 점수 (0~100)
-- 3) temperature_history.activity_id : 분석 출처 활동 ID
-- =====================================================

-- 1. activities 에 mood_status 추가
ALTER TABLE activities
    ADD COLUMN mood_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE activities
    ADD CONSTRAINT activities_mood_status_check
        CHECK (mood_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));

-- 2. temperature_history 에 mood_score 추가
ALTER TABLE temperature_history
    ADD COLUMN mood_score INTEGER NOT NULL DEFAULT 0;

ALTER TABLE temperature_history
    ADD CONSTRAINT temperature_history_mood_score_check
        CHECK (mood_score BETWEEN 0 AND 100);

-- 3. temperature_history 에 activity_id 추가 (Nullable — 수동 입력 케이스 대비)
ALTER TABLE temperature_history
    ADD COLUMN activity_id BIGINT NULL REFERENCES activities(activity_id);

CREATE INDEX idx_temperature_history_activity
    ON temperature_history(activity_id);
