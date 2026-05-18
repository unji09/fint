UPDATE ai_suggestions
SET importance_score = ROUND((importance_score / 20.0)::numeric, 1)
WHERE importance_score > 5.0;
