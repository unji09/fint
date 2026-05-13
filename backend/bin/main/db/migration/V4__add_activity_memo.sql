-- =====================================================
-- F!NT B2B Sales CRM - V4 활동 컬럼 보강
-- 1) memo 컬럼 추가 (REQ-ACT-04 응답 필드)
-- 2) type CHECK 제약 추가 (ActivityType enum 외 값 차단)
-- =====================================================

ALTER TABLE activities ADD COLUMN memo TEXT NULL;

ALTER TABLE activities
    ADD CONSTRAINT activities_type_check
        CHECK (type IN ('MEETING', 'CALL', 'TASK', 'EMAIL'));
