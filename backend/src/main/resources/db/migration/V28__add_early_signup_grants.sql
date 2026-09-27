CREATE TABLE early_signup_grant_counter (
    id              TINYINT NOT NULL,
    assigned_count  INT     NOT NULL,

    CONSTRAINT pk_early_signup_grant_counter PRIMARY KEY (id)
) ENGINE = INNODB;

INSERT INTO early_signup_grant_counter (id, assigned_count)
VALUES (1, 0);

CREATE TABLE early_signup_grants (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    di_hash     TEXT NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    granted_at  DATETIME(6),
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_early_signup_grants PRIMARY KEY (id),
    CONSTRAINT uk_early_signup_grants_user_id UNIQUE (user_id),
    CONSTRAINT uk_early_signup_grants_di_hash UNIQUE (di_hash(255)),
    CONSTRAINT fk_early_signup_grants_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;
