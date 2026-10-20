ALTER TABLE balance_game_rounds
    ADD COLUMN season_id BIGINT NULL AFTER question_id;

UPDATE balance_game_rounds r
SET r.season_id = (
    SELECT s.id
    FROM seasons s
    WHERE s.created_at <= r.created_at
    ORDER BY s.created_at DESC, s.id DESC
    LIMIT 1
);

ALTER TABLE balance_game_rounds
    ADD CONSTRAINT fk_balance_game_rounds_season_id FOREIGN KEY (season_id) REFERENCES seasons(id);
