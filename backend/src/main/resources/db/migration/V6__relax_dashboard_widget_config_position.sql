-- =====================================================
-- F!NT B2B Sales CRM - V6 dashboard_widgets NULL 허용 보정 (ERD 반영)
-- 1) config / position : ERD 0507 수정 반영, NOT NULL 해제
-- 2) dashboard_query_id : ERD 원본 기준 NULL 허용. V3 에서 NOT NULL 로 추가됐으나
--    ERD 는 위젯이 쿼리 없이도 존재 가능한 모델(NULL)이므로 정합성 보정.
-- dashboard_id 는 V2 시점부터 NULL 이라 별도 조치 없음.
-- =====================================================

ALTER TABLE dashboard_widgets
    ALTER COLUMN config DROP NOT NULL;

ALTER TABLE dashboard_widgets
    ALTER COLUMN position DROP NOT NULL;

ALTER TABLE dashboard_widgets
    ALTER COLUMN dashboard_query_id DROP NOT NULL;
