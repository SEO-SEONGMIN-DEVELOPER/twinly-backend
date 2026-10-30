ALTER TABLE scenes
    ADD COLUMN season_id BIGINT NULL,
    ALGORITHM = INSTANT;

CREATE INDEX ix_scenes_season_id_date ON scenes (season_id, date) ALGORITHM = INPLACE LOCK = NONE;

UPDATE scenes sc
SET sc.season_id = (
    SELECT s.id
    FROM seasons s
    WHERE s.started_at < TIMESTAMPADD(HOUR, 15, sc.date)
      AND TIMESTAMPADD(HOUR, -9, sc.date) < s.ended_at
    ORDER BY s.started_at DESC, s.id DESC
    LIMIT 1
);
