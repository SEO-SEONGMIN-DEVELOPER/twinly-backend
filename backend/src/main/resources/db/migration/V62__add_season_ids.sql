ALTER TABLE seasons
    ADD COLUMN activated_at DATETIME(6) NULL;

UPDATE seasons
SET activated_at = created_at;

ALTER TABLE relationships
    ADD COLUMN season_id BIGINT NULL,
    ADD CONSTRAINT fk_relationships_season_id FOREIGN KEY (season_id) REFERENCES seasons(id),
    ADD INDEX ix_relationships_user_id_season_id_date (user_id, season_id, date);

ALTER TABLE intimacy_bonuses
    ADD COLUMN season_id BIGINT NULL,
    ADD CONSTRAINT fk_intimacy_bonuses_season_id FOREIGN KEY (season_id) REFERENCES seasons(id);

UPDATE relationships r
SET r.season_id = (
    SELECT s.id
    FROM seasons s
    WHERE s.started_at < TIMESTAMPADD(HOUR, 15, r.date)
      AND TIMESTAMPADD(HOUR, -9, r.date) < s.ended_at
    ORDER BY s.started_at DESC, s.id DESC
    LIMIT 1
);

UPDATE intimacy_bonuses b
SET b.season_id = (
    SELECT s.id
    FROM seasons s
    WHERE s.started_at <= b.created_at
      AND b.created_at < s.ended_at
    ORDER BY s.started_at DESC, s.id DESC
    LIMIT 1
);
