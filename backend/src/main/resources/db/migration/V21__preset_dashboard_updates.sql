-- 프리셋 위젯 쿼리 수정 + 두 번째 템플릿 그룹 추가
-- 1. 계약 만료 임박 (ID=7): 30일 → 7일, 이미 지난 날짜 제외
-- 2. 고객 등급 분포 (ID=5): mood 기반 → 딜 스테이지별 분포로 교체
-- 3. 두 번째 프리셋 대시보드 템플릿 그룹 (ID 9~16, groupId=2) - 주제: 활동 & 고객 분석

UPDATE dashboard_templates
SET source_query =
    'SELECT d.title AS "dealTitle", a.name AS "accountName", d.expected_close AS "expectedClose", d.amount'
    ' FROM deals d'
    ' JOIN accounts a ON d.account_id = a.account_id'
    ' JOIN account_user_assignment aua ON a.account_id = aua.account_id'
    ' JOIN users u ON aua.user_id = u.user_id'
    ' WHERE u.tenant_id = :tenantId'
    ' AND d.is_deleted = FALSE'
    ' AND d.won_at IS NULL AND d.lost_at IS NULL'
    ' AND d.expected_close IS NOT NULL'
    ' AND d.expected_close >= CURRENT_DATE'
    ' AND d.expected_close <= CURRENT_DATE + INTERVAL ''7 days'''
    ' ORDER BY d.expected_close ASC'
    ' LIMIT 10'
WHERE dashboard_template_id = 7;

UPDATE dashboard_templates
SET
    title = '딜 스테이지 분포',
    config = '{
        "chart": {"type": "doughnut"},
        "data": {
            "labelsField": "stage",
            "datasets": [{"label": "딜 수", "valueField": "count"}]
        },
        "options": {"legend": true},
        "display": {"format": "number", "emptyMessage": "진행 중인 딜이 없습니다"}
    }',
    source_query =
        'SELECT COALESCE(d.current_pipeline, ''미지정'') AS stage, COUNT(*) AS count'
        ' FROM deals d'
        ' JOIN accounts a ON d.account_id = a.account_id'
        ' JOIN account_user_assignment aua ON a.account_id = aua.account_id'
        ' JOIN users u ON aua.user_id = u.user_id'
        ' WHERE u.tenant_id = :tenantId'
        ' AND d.is_deleted = FALSE'
        ' AND d.won_at IS NULL AND d.lost_at IS NULL'
        ' GROUP BY stage'
        ' ORDER BY count DESC'
WHERE dashboard_template_id = 5;

INSERT INTO dashboard_templates (dashboard_template_id, widget_type, title, config, position, source_query)
VALUES

-- 9. 고객사별 딜 금액 합계 (막대 차트)
(
    9,
    'CHART',
    '고객사별 딜 금액',
    '{
        "chart": {"type": "bar"},
        "data": {
            "labelsField": "accountName",
            "datasets": [{"label": "딜 금액", "valueField": "totalAmount"}]
        },
        "options": {
            "xAxis": {"label": "고객사"},
            "yAxis": {"label": "금액", "unit": "원"},
            "legend": false
        },
        "display": {"format": "currency", "koreanUnit": true, "emptyMessage": "딜 데이터가 없습니다"}
    }',
    '{"x": 0, "y": 0, "w": 6, "h": 4}',
    'SELECT a.name AS "accountName", COALESCE(SUM(d.amount), 0) AS "totalAmount"
     FROM accounts a
     JOIN account_user_assignment aua ON a.account_id = aua.account_id
     JOIN users u ON aua.user_id = u.user_id
     LEFT JOIN deals d ON d.account_id = a.account_id AND d.is_deleted = FALSE AND d.won_at IS NULL AND d.lost_at IS NULL
     WHERE u.tenant_id = :tenantId AND a.is_deleted = FALSE
     GROUP BY a.account_id, a.name
     ORDER BY "totalAmount" DESC
     LIMIT 10'
),

-- 10. 활동 유형별 통계 (도넛 차트)
(
    10,
    'CHART',
    '활동 유형별 통계',
    '{
        "chart": {"type": "doughnut"},
        "data": {
            "labelsField": "activityType",
            "datasets": [{"label": "건수", "valueField": "count"}]
        },
        "options": {"legend": true},
        "display": {"format": "number", "emptyMessage": "활동 데이터가 없습니다"}
    }',
    '{"x": 6, "y": 0, "w": 6, "h": 4}',
    'SELECT act.type AS "activityType", COUNT(*) AS count
     FROM activities act
     JOIN users u ON act.user_id = u.user_id
     WHERE u.tenant_id = :tenantId
       AND act.start_at >= DATE_TRUNC(''month'', CURRENT_DATE)
       AND act.start_at < DATE_TRUNC(''month'', CURRENT_DATE) + INTERVAL ''1 month''
     GROUP BY act.type
     ORDER BY count DESC'
),

-- 11. 담당자별 활동량 (막대 차트)
(
    11,
    'CHART',
    '담당자별 활동량',
    '{
        "chart": {"type": "bar"},
        "data": {
            "labelsField": "userName",
            "datasets": [{"label": "활동 건수", "valueField": "count"}]
        },
        "options": {
            "xAxis": {"label": "담당자"},
            "yAxis": {"label": "건수", "unit": "건"},
            "legend": false
        },
        "display": {"format": "number", "emptyMessage": "활동 데이터가 없습니다"}
    }',
    '{"x": 0, "y": 4, "w": 6, "h": 4}',
    'SELECT u.name AS "userName", COUNT(*) AS count
     FROM activities act
     JOIN users u ON act.user_id = u.user_id
     WHERE u.tenant_id = :tenantId
       AND act.start_at >= DATE_TRUNC(''month'', CURRENT_DATE)
     GROUP BY u.user_id, u.name
     ORDER BY count DESC
     LIMIT 10'
),

-- 12. 이번 달 활동 목록 (테이블)
(
    12,
    'TABLE',
    '이번 달 활동 목록',
    '{
        "columns": [
            {"label": "유형", "field": "activityType", "format": "text"},
            {"label": "제목", "field": "activityTitle", "format": "text"},
            {"label": "고객사", "field": "accountName", "format": "text"},
            {"label": "담당자", "field": "userName", "format": "text"},
            {"label": "일시", "field": "startAt", "format": "datetime"}
        ],
        "display": {"emptyMessage": "이번 달 활동이 없습니다"}
    }',
    '{"x": 6, "y": 4, "w": 6, "h": 4}',
    'SELECT act.type AS "activityType", act.title AS "activityTitle",
            a.name AS "accountName", u.name AS "userName", act.start_at AS "startAt"
     FROM activities act
     JOIN users u ON act.user_id = u.user_id
     LEFT JOIN deals d ON act.deal_id = d.deal_id
     LEFT JOIN accounts a ON d.account_id = a.account_id
     WHERE u.tenant_id = :tenantId
       AND act.start_at >= DATE_TRUNC(''month'', CURRENT_DATE)
       AND act.start_at < DATE_TRUNC(''month'', CURRENT_DATE) + INTERVAL ''1 month''
     ORDER BY act.start_at DESC
     LIMIT 20'
),

-- 13. 수주 완료 딜 목록 (테이블)
(
    13,
    'TABLE',
    '수주 완료 딜',
    '{
        "columns": [
            {"label": "딜 제목", "field": "dealTitle", "format": "text"},
            {"label": "고객사", "field": "accountName", "format": "text"},
            {"label": "금액", "field": "amount", "format": "currency", "unit": "원"},
            {"label": "수주일", "field": "wonAt", "format": "date"},
            {"label": "담당자", "field": "userName", "format": "text"}
        ],
        "display": {"emptyMessage": "수주 완료 딜이 없습니다"}
    }',
    '{"x": 0, "y": 8, "w": 6, "h": 4}',
    'SELECT d.title AS "dealTitle", a.name AS "accountName",
            d.amount, d.won_at AS "wonAt", u.name AS "userName"
     FROM deals d
     JOIN accounts a ON d.account_id = a.account_id
     JOIN account_user_assignment aua ON a.account_id = aua.account_id
     JOIN users u ON aua.user_id = u.user_id
     WHERE u.tenant_id = :tenantId
       AND d.is_deleted = FALSE
       AND d.won_at IS NOT NULL
     ORDER BY d.won_at DESC
     LIMIT 15'
),

-- 14. 담당자별 매출 현황 (막대 차트)
(
    14,
    'CHART',
    '담당자별 매출 현황',
    '{
        "chart": {"type": "bar"},
        "data": {
            "labelsField": "userName",
            "datasets": [{"label": "수주 금액", "valueField": "totalWon"}]
        },
        "options": {
            "xAxis": {"label": "담당자"},
            "yAxis": {"label": "매출", "unit": "원"},
            "legend": false
        },
        "display": {"format": "currency", "koreanUnit": true, "emptyMessage": "매출 데이터가 없습니다"}
    }',
    '{"x": 6, "y": 8, "w": 6, "h": 4}',
    'SELECT u.name AS "userName", COALESCE(SUM(d.amount), 0) AS "totalWon"
     FROM users u
     JOIN account_user_assignment aua ON aua.user_id = u.user_id
     LEFT JOIN accounts a ON a.account_id = aua.account_id AND a.is_deleted = FALSE
     LEFT JOIN deals d ON d.account_id = a.account_id AND d.is_deleted = FALSE AND d.won_at IS NOT NULL
     WHERE u.tenant_id = :tenantId
     GROUP BY u.user_id, u.name
     ORDER BY "totalWon" DESC
     LIMIT 10'
),

-- 15. 신규 고객 현황 (테이블)
(
    15,
    'TABLE',
    '신규 고객 현황',
    '{
        "columns": [
            {"label": "고객사", "field": "accountName", "format": "text"},
            {"label": "업종", "field": "industry", "format": "text"},
            {"label": "담당자", "field": "userName", "format": "text"},
            {"label": "등록일", "field": "createdAt", "format": "date"}
        ],
        "display": {"emptyMessage": "신규 고객이 없습니다"}
    }',
    '{"x": 0, "y": 12, "w": 6, "h": 4}',
    'SELECT a.name AS "accountName", a.industry, u.name AS "userName", a.created_at AS "createdAt"
     FROM accounts a
     JOIN account_user_assignment aua ON a.account_id = aua.account_id
     JOIN users u ON aua.user_id = u.user_id
     WHERE u.tenant_id = :tenantId
       AND a.is_deleted = FALSE
       AND a.created_at >= NOW() - INTERVAL ''90 days''
     ORDER BY a.created_at DESC
     LIMIT 15'
),

-- 16. 고객사별 최근 무드 분석 (막대 차트)
(
    16,
    'CHART',
    '고객사별 최근 무드',
    '{
        "chart": {"type": "bar"},
        "data": {
            "labelsField": "accountName",
            "datasets": [{"label": "무드 점수", "valueField": "moodScore"}]
        },
        "options": {
            "xAxis": {"label": "고객사"},
            "yAxis": {"label": "무드", "unit": "mood"},
            "legend": false
        },
        "display": {"format": "mood", "emptyMessage": "무드 데이터가 없습니다"}
    }',
    '{"x": 6, "y": 12, "w": 6, "h": 4}',
    'SELECT a.name AS "accountName",
            CASE th.mood
                WHEN ''THUNDER'' THEN 0
                WHEN ''RAINY''   THEN 0.25
                WHEN ''CLOUDY''  THEN 0.5
                WHEN ''SUNNY''   THEN 0.75
                WHEN ''RAINBOW'' THEN 1.0
                ELSE 0.5
            END AS "moodScore"
     FROM accounts a
     JOIN account_user_assignment aua ON a.account_id = aua.account_id
     JOIN users u ON aua.user_id = u.user_id
     JOIN LATERAL (
         SELECT mood FROM temperature_history
         WHERE account_id = a.account_id
         ORDER BY created_at DESC LIMIT 1
     ) th ON TRUE
     WHERE u.tenant_id = :tenantId AND a.is_deleted = FALSE
     ORDER BY "moodScore" DESC
     LIMIT 10'
);

SELECT setval('dashboard_templates_dashboard_template_id_seq', 16);
