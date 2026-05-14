UPDATE ai_suggestions SET related_type = UPPER(related_type)
    WHERE related_type <> UPPER(related_type);
