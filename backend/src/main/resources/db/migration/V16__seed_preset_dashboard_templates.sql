-- 기존 시드 템플릿 삭제 후 프리셋 대시보드 템플릿 8개 삽입
DELETE FROM dashboard_templates;

INSERT INTO dashboard_templates (widget_type, title, config, position, source_query)
VALUES
-- 1. 기본 성과: 월별 매출 추이 (막대 차트)
(
    'CHART',
    '월별 매출 추이',
    '{
        "chart": {"type": "bar"},
        "data": {
            "labelsField": "month",
            "datasets": [{"label": "매출", "valueField": "totalAmount"}]
        },
        "options": {
            "xAxis": {"label": "월"},
            "yAxis": {"label": "매출", "unit": "원"},
            "legend": true
        },
        "display": {"format": "currency", "emptyMessage": "조회된 매출 데이터가 없습니다"}
    }',
    '{"x": 0, "y": 0, "w": 6, "h": 4}',
    'SELECT TO_CHAR(d.won_at, ''YYYY-MM'') AS month, SUM(d.amount) AS "totalAmount" FROM deals d JOIN accounts a ON d.account_id = a.account_id JOIN account_user_assignment aua ON a.account_id = aua.account_id JOIN users u ON aua.user_id = u.user_id WHERE u.tenant_id = :tenantId AND d.is_deleted = FALSE AND d.won_at IS NOT NULL AND d.won_at >= NOW() - INTERVAL ''6 months'' GROUP BY month ORDER BY month'
),

-- 2. 파이프라인: 딜 단계별 현황 (막대 차트)
(
    'CHART',
    '딜 파이프라인 현황',
    '{
        "chart": {"type": "bar"},
        "data": {
            "labelsField": "stage",
            "datasets": [{"label": "건수", "valueField": "count"}]
        },
        "options": {
            "xAxis": {"label": "단계"},
            "yAxis": {"label": "건수", "unit": "건"},
            "legend": false
        },
        "display": {"format": "number", "emptyMessage": "진행 중인 딜이 없습니다"}
    }',
    '{"x": 6, "y": 0, "w": 6, "h": 4}',
    'SELECT COALESCE(d.current_pipeline, ''미지정'') AS stage, COUNT(*) AS count FROM deals d JOIN accounts a ON d.account_id = a.account_id JOIN account_user_assignment aua ON a.account_id = aua.account_id JOIN users u ON aua.user_id = u.user_id WHERE u.tenant_id = :tenantId AND d.is_deleted = FALSE AND d.won_at IS NULL AND d.lost_at IS NULL GROUP BY stage ORDER BY count DESC'
),

-- 3. 전환율: 단계별 딜 전환율 (선 차트)
(
    'CHART',
    '단계별 전환율',
    '{
        "chart": {"type": "line"},
        "data": {
            "labelsField": "stage",
            "datasets": [{"label": "전환율", "valueField": "ratio"}]
        },
        "options": {
            "xAxis": {"label": "단계"},
            "yAxis": {"label": "전환율", "unit": "%"},
            "legend": false
        },
        "display": {"format": "percent", "emptyMessage": "전환율 데이터가 없습니다"}
    }',
    '{"x": 0, "y": 4, "w": 6, "h": 4}',
    'SELECT ps.name AS stage, CASE WHEN COUNT(*) = 0 THEN 0 ELSE ROUND(COUNT(CASE WHEN d.won_at IS NOT NULL THEN 1 END) * 100.0 / COUNT(*), 1) END AS ratio FROM pipeline_stages ps LEFT JOIN deals d ON d.current_pipeline = ps.name AND d.is_deleted = FALSE LEFT JOIN accounts a ON d.account_id = a.account_id LEFT JOIN account_user_assignment aua ON a.account_id = aua.account_id LEFT JOIN users u ON aua.user_id = u.user_id WHERE ps.tenant_id = :tenantId GROUP BY ps.name, ps.sort_order ORDER BY ps.sort_order'
),

-- 4. 고객 관리: 고객 리스트 + 최근 접촉일 (테이블)
(
    'TABLE',
    '고객 리스트',
    '{
        "columns": [
            {"label": "고객명", "field": "accountName", "format": "text"},
            {"label": "업종", "field": "industry", "format": "text"},
            {"label": "최근 접촉일", "field": "lastContactDate", "format": "date"},
            {"label": "진행 딜 수", "field": "dealCount", "format": "number"}
        ],
        "display": {"emptyMessage": "등록된 고객이 없습니다"}
    }',
    '{"x": 6, "y": 4, "w": 6, "h": 4}',
    'SELECT a.name AS "accountName", a.industry, MAX(act.start_at)::DATE AS "lastContactDate", COUNT(DISTINCT d.deal_id) AS "dealCount" FROM accounts a JOIN account_user_assignment aua ON a.account_id = aua.account_id JOIN users u ON aua.user_id = u.user_id LEFT JOIN deals d ON d.account_id = a.account_id AND d.is_deleted = FALSE AND d.won_at IS NULL AND d.lost_at IS NULL LEFT JOIN activities act ON act.deal_id = d.deal_id WHERE u.tenant_id = :tenantId AND a.is_deleted = FALSE GROUP BY a.account_id, a.name, a.industry ORDER BY "lastContactDate" DESC NULLS LAST LIMIT 20'
),

-- 5. 고객 분포: 고객 등급(mood)별 분포 (파이 차트)
(
    'CHART',
    '고객 등급 분포',
    '{
        "chart": {"type": "doughnut"},
        "data": {
            "labelsField": "grade",
            "datasets": [{"label": "고객 수", "valueField": "count"}]
        },
        "options": {"legend": true},
        "display": {"format": "number", "emptyMessage": "고객 등급 데이터가 없습니다"}
    }',
    '{"x": 0, "y": 8, "w": 4, "h": 4}',
    'SELECT th.mood AS grade, COUNT(DISTINCT a.account_id) AS count FROM accounts a JOIN account_user_assignment aua ON a.account_id = aua.account_id JOIN users u ON aua.user_id = u.user_id JOIN LATERAL (SELECT mood FROM temperature_history WHERE account_id = a.account_id ORDER BY created_at DESC LIMIT 1) th ON TRUE WHERE u.tenant_id = :tenantId AND a.is_deleted = FALSE GROUP BY th.mood ORDER BY count DESC'
),

-- 6. 리스크: 정체된 딜 목록 (테이블)
(
    'TABLE',
    '정체 딜 리스트',
    '{
        "columns": [
            {"label": "딜 제목", "field": "dealTitle", "format": "text"},
            {"label": "고객명", "field": "accountName", "format": "text"},
            {"label": "현재 단계", "field": "stage", "format": "text"},
            {"label": "정체 일수", "field": "stalledDays", "format": "number"}
        ],
        "display": {"emptyMessage": "정체된 딜이 없습니다"}
    }',
    '{"x": 4, "y": 8, "w": 4, "h": 4}',
    'SELECT d.title AS "dealTitle", a.name AS "accountName", COALESCE(d.current_pipeline, ''미지정'') AS stage, EXTRACT(DAY FROM NOW() - d.updated_at)::INT AS "stalledDays" FROM deals d JOIN accounts a ON d.account_id = a.account_id JOIN account_user_assignment aua ON a.account_id = aua.account_id JOIN users u ON aua.user_id = u.user_id WHERE u.tenant_id = :tenantId AND d.is_deleted = FALSE AND d.won_at IS NULL AND d.lost_at IS NULL AND d.updated_at < NOW() - INTERVAL ''14 days'' ORDER BY "stalledDays" DESC LIMIT 10'
),

-- 7. 계약 관리: 만료 임박 딜 (테이블)
(
    'TABLE',
    '계약 만료 임박',
    '{
        "columns": [
            {"label": "딜 제목", "field": "dealTitle", "format": "text"},
            {"label": "고객명", "field": "accountName", "format": "text"},
            {"label": "예상 마감일", "field": "expectedClose", "format": "date"},
            {"label": "금액", "field": "amount", "format": "currency", "unit": "원"}
        ],
        "display": {"emptyMessage": "만료 임박 건이 없습니다"}
    }',
    '{"x": 8, "y": 8, "w": 4, "h": 4}',
    'SELECT d.title AS "dealTitle", a.name AS "accountName", d.expected_close AS "expectedClose", d.amount FROM deals d JOIN accounts a ON d.account_id = a.account_id JOIN account_user_assignment aua ON a.account_id = aua.account_id JOIN users u ON aua.user_id = u.user_id WHERE u.tenant_id = :tenantId AND d.is_deleted = FALSE AND d.won_at IS NULL AND d.lost_at IS NULL AND d.expected_close IS NOT NULL AND d.expected_close <= CURRENT_DATE + INTERVAL ''30 days'' ORDER BY d.expected_close ASC LIMIT 10'
),

-- 8. 활동 로그: 이번 주 활동 기록 (테이블)
(
    'TABLE',
    '이번 주 활동 로그',
    '{
        "columns": [
            {"label": "유형", "field": "activityType", "format": "text"},
            {"label": "제목", "field": "activityTitle", "format": "text"},
            {"label": "고객명", "field": "accountName", "format": "text"},
            {"label": "일시", "field": "startAt", "format": "date"}
        ],
        "display": {"emptyMessage": "이번 주 활동 기록이 없습니다"}
    }',
    '{"x": 0, "y": 12, "w": 12, "h": 4}',
    'SELECT act.type AS "activityType", act.title AS "activityTitle", a.name AS "accountName", act.start_at AS "startAt" FROM activities act JOIN users u ON act.user_id = u.user_id LEFT JOIN deals d ON act.deal_id = d.deal_id LEFT JOIN accounts a ON d.account_id = a.account_id WHERE u.tenant_id = :tenantId AND act.start_at >= DATE_TRUNC(''week'', CURRENT_DATE) AND act.start_at < DATE_TRUNC(''week'', CURRENT_DATE) + INTERVAL ''7 days'' ORDER BY act.start_at DESC LIMIT 20'
);
