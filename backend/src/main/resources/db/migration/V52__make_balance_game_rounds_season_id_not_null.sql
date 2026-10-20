UPDATE balance_game_rounds r
SET r.season_id = (
    SELECT s.id
    FROM seasons s
    WHERE s.created_at <= r.created_at
    ORDER BY s.created_at DESC, s.id DESC
    LIMIT 1
)
WHERE r.season_id IS NULL;

ALTER TABLE balance_game_rounds
    MODIFY COLUMN season_id BIGINT NOT NULL;
