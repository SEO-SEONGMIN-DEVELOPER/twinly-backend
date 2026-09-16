CREATE TABLE identity_verification_logs (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    anon_session_id  BIGINT NOT NULL,
    request_no       VARCHAR(64) NOT NULL,
    transaction_id   VARCHAR(255) NOT NULL,
    di_hash          TEXT,
    result           ENUM ('ISSUED','VERIFIED','AGE_NOT_ALLOWED','ALREADY_REGISTERED','INVALID_RESULT') NOT NULL,
    resulted_at      DATETIME(6),
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_identity_verification_logs PRIMARY KEY (id)
) ENGINE = INNODB;

CREATE INDEX ix_identity_verification_logs_transaction_id ON identity_verification_logs (transaction_id);
CREATE INDEX ix_identity_verification_logs_di_hash ON identity_verification_logs (di_hash(255));
