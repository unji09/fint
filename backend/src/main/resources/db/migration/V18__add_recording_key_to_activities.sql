ALTER TABLE activities
    ADD COLUMN recording_key VARCHAR(100) NULL;

ALTER TABLE ai_suggestions
    ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN success_probability INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_ai_suggestions_success_probability
        CHECK (success_probability >= 0 AND success_probability <= 100);
