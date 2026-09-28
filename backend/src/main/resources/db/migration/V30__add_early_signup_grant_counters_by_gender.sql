CREATE TABLE early_signup_grant_counters (
    gender          ENUM ('MALE','FEMALE') NOT NULL,
    assigned_count  INT NOT NULL,

    CONSTRAINT pk_early_signup_grant_counters PRIMARY KEY (gender)
) ENGINE = INNODB;

INSERT INTO early_signup_grant_counters (gender, assigned_count)
SELECT 'MALE', COUNT(*)
FROM early_signup_grants g
    JOIN users u ON u.id = g.user_id
WHERE u.gender = 'MALE'
UNION ALL
SELECT 'FEMALE', COUNT(*)
FROM early_signup_grants g
    JOIN users u ON u.id = g.user_id
WHERE u.gender = 'FEMALE';

DROP TABLE early_signup_grant_counter;
