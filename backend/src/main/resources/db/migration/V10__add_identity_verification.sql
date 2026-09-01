CREATE TABLE anon_session_identity_verifications (
    id                        BIGINT NOT NULL AUTO_INCREMENT,
    anon_session_id           BIGINT NOT NULL,
    identity_verification_id  VARCHAR(64) NOT NULL,
    expires_at                DATETIME(6) NOT NULL,
    verified_at               DATETIME(6),
    name                      TEXT,
    birth_date                TEXT,
    gender                    ENUM ('MALE','FEMALE'),
    phone_number              TEXT,
    ci                        TEXT,
    ci_hash                   TEXT,
    issue_window_started_at   DATETIME(6) NOT NULL,
    issue_count               INT NOT NULL,
    created_at                DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_session_identity_verifications PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_identity_verifications_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id),
    CONSTRAINT uk_anon_session_identity_verifications_anon_session_id UNIQUE (anon_session_id),
    CONSTRAINT uk_anon_session_identity_verifications_verification_id UNIQUE (identity_verification_id)
) ENGINE = INNODB;

ALTER TABLE users
    ADD COLUMN ci      TEXT,
    ADD COLUMN ci_hash TEXT,
    ADD CONSTRAINT uk_users_ci_hash UNIQUE (ci_hash(255));

ALTER TABLE verifications
    MODIFY type ENUM ('SMS','EMAIL','IDENTITY') NOT NULL;
