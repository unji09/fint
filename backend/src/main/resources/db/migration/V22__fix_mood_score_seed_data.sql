-- V8 seed data was inserted before V11 added mood_score (DEFAULT 0).
-- Backfill correct scores based on the 5-step mood enum.
UPDATE temperature_history
SET mood_score = CASE mood
    WHEN 'RAINBOW' THEN 90
    WHEN 'SUNNY'   THEN 70
    WHEN 'CLOUDY'  THEN 50
    WHEN 'RAINY'   THEN 30
    WHEN 'THUNDER' THEN 10
    ELSE 0
END
WHERE mood_score = 0 AND mood IS NOT NULL;
