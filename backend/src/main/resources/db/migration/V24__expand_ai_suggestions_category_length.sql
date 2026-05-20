-- ai_suggestions.category VARCHAR(50) → VARCHAR(200)
-- 사유: AI 응답의 고정 카테고리 중 최장 52자("Stakeholder-Specific (CIO/CISO/IT/Procurement/Finance)")가 기존 한도를 초과해 mood 콜백 트랜잭션이 롤백되던 문제 해소.
ALTER TABLE ai_suggestions
    ALTER COLUMN category TYPE VARCHAR(200);
