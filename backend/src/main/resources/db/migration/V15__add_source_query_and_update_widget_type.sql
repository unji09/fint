-- 1) dashboard_templates에 source_query 컬럼 추가
ALTER TABLE dashboard_templates
    ADD COLUMN source_query TEXT NULL;

-- 2) widget_type 값 마이그레이션: 기존 세부 타입 → 새 대분류
UPDATE dashboard_templates SET widget_type = 'CHART' WHERE widget_type IN ('BAR_CHART', 'LINE_CHART', 'PIE');
UPDATE dashboard_templates SET widget_type = 'CARD'  WHERE widget_type = 'KPI';
UPDATE dashboard_templates SET widget_type = 'CHART' WHERE widget_type = 'NL_QUERY';

UPDATE dashboard_widgets SET widget_type = 'CHART' WHERE widget_type IN ('BAR_CHART', 'LINE_CHART', 'PIE');
UPDATE dashboard_widgets SET widget_type = 'CARD'  WHERE widget_type = 'KPI';
UPDATE dashboard_widgets SET widget_type = 'CHART' WHERE widget_type = 'NL_QUERY';
