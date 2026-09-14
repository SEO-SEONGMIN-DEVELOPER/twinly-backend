ALTER TABLE users
    ADD COLUMN pool_number INT;

UPDATE users u
    JOIN (
        SELECT user_id, ROW_NUMBER() OVER (ORDER BY id) AS seq
        FROM user_entitlements
        WHERE entitlement = 'simulation_access'
    ) e ON e.user_id = u.id
SET u.pool_number = (e.seq - 1) DIV 50 + 1;

CREATE TABLE pool_counter (
    id              TINYINT NOT NULL,
    assigned_count  INT     NOT NULL,

    CONSTRAINT pk_pool_counter PRIMARY KEY (id)
) ENGINE = INNODB;

INSERT INTO pool_counter (id, assigned_count)
SELECT 1, COUNT(*)
FROM users
WHERE pool_number IS NOT NULL;
