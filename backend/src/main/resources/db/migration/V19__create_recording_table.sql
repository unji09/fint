-- REQ-ACT-01: 활동별 녹음본을 별도 테이블로 분리 (1:N 구조)

-- 1. recordings 테이블 생성
CREATE TABLE recordings (
    recording_id BIGSERIAL PRIMARY KEY,
    activity_id  BIGINT       NOT NULL,
    tenant_id    BIGINT       NOT NULL,
    file_key     VARCHAR(200) NOT NULL,
    title        VARCHAR(300) NOT NULL DEFAULT '',
    duration     INTEGER      NOT NULL DEFAULT 0,
    stt_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    transcript   JSONB,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_recordings_activity
        FOREIGN KEY (activity_id) REFERENCES activities(activity_id) ON DELETE CASCADE
);

CREATE INDEX idx_recordings_activity_id ON recordings(activity_id);
CREATE INDEX idx_recordings_tenant_id   ON recordings(tenant_id);

-- 2. 기존 activities의 녹음 데이터 이관 (recording_key IS NOT NULL 인 행만)
INSERT INTO recordings (activity_id, tenant_id, file_key, title, duration, stt_status, transcript, created_at, updated_at)
SELECT
    a.activity_id,
    u.tenant_id,
    a.recording_key,
    a.title,
    0,
    COALESCE(a.stt_status, 'PENDING'),
    a.transcript,
    a.created_at,
    a.updated_at
FROM activities a
JOIN users u ON a.user_id = u.user_id
WHERE a.recording_key IS NOT NULL;

-- 3. activities에서 녹음/STT 관련 컬럼 제거 및 briefing 추가
ALTER TABLE activities DROP COLUMN IF EXISTS recording_key;
ALTER TABLE activities DROP COLUMN IF EXISTS stt_status;
ALTER TABLE activities DROP COLUMN IF EXISTS transcript;
ALTER TABLE activities ADD COLUMN IF NOT EXISTS briefing JSONB NULL;

-- 4. recordings 변경 시 activities.updated_at 자동 동기화 트리거
CREATE OR REPLACE FUNCTION fn_touch_activity_on_recording_change()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE activities SET updated_at = NOW() WHERE activity_id = OLD.activity_id;
    ELSE
        UPDATE activities SET updated_at = NOW() WHERE activity_id = NEW.activity_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_touch_activity_on_recording_change
AFTER INSERT OR UPDATE OR DELETE ON recordings
FOR EACH ROW EXECUTE FUNCTION fn_touch_activity_on_recording_change();
