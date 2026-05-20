-- V23: 프리셋 무드 위젯(ID=16) JOIN LATERAL → LEFT JOIN LATERAL
-- 기존: INNER JOIN LATERAL → temperature_history 없는 고객사 제외
-- 변경: LEFT JOIN LATERAL → 모든 고객사 표시, 기록 없으면 ELSE 0.5(흐림) 기본값 사용

UPDATE dashboard_templates
SET source_query =
    'SELECT a.name AS "accountName",'
    '       CASE th.mood'
    '           WHEN ''THUNDER'' THEN 0'
    '           WHEN ''RAINY''   THEN 0.25'
    '           WHEN ''CLOUDY''  THEN 0.5'
    '           WHEN ''SUNNY''   THEN 0.75'
    '           WHEN ''RAINBOW'' THEN 1.0'
    '           ELSE 0.5'
    '       END AS "moodScore"'
    ' FROM accounts a'
    ' JOIN account_user_assignment aua ON a.account_id = aua.account_id'
    ' JOIN users u ON aua.user_id = u.user_id'
    ' LEFT JOIN LATERAL ('
    '     SELECT mood FROM temperature_history'
    '     WHERE account_id = a.account_id'
    '     ORDER BY created_at DESC LIMIT 1'
    ' ) th ON TRUE'
    ' WHERE u.tenant_id = :tenantId AND a.is_deleted = FALSE'
    ' ORDER BY "moodScore" DESC'
    ' LIMIT 10'
WHERE dashboard_template_id = 16;
