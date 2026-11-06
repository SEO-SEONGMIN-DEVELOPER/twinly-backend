CREATE TABLE twin_creations (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    di_hash     TEXT,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_twin_creations PRIMARY KEY (id),
    CONSTRAINT uk_twin_creations_user_id UNIQUE (user_id),
    CONSTRAINT fk_twin_creations_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_twin_creations_di_hash (di_hash(255))
) ENGINE = INNODB;

INSERT INTO twin_creations (user_id, di_hash, created_at)
SELECT id, di_hash, ai_chat_completed_at
FROM users
WHERE ai_chat_completed_at IS NOT NULL;

CREATE TABLE invite_registrations (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    inviter_user_id  BIGINT NOT NULL,
    invitee_user_id  BIGINT NOT NULL,
    registered_at    DATETIME(6) NOT NULL,
    completed_at     DATETIME(6),

    CONSTRAINT pk_invite_registrations PRIMARY KEY (id),
    CONSTRAINT uk_invite_registrations_invitee_user_id UNIQUE (invitee_user_id),
    CONSTRAINT fk_invite_registrations_inviter_user_id FOREIGN KEY (inviter_user_id) REFERENCES users(id),
    CONSTRAINT fk_invite_registrations_invitee_user_id FOREIGN KEY (invitee_user_id) REFERENCES users(id),
    INDEX idx_invite_registrations_inviter_completed (inviter_user_id, completed_at)
) ENGINE = INNODB;

CREATE TABLE invite_rewards (
    id                        BIGINT NOT NULL AUTO_INCREMENT,
    user_id                   BIGINT NOT NULL,
    di_hash                   TEXT,
    status                    VARCHAR(32) NOT NULL,
    waiting_reason            VARCHAR(32),
    method                    VARCHAR(32),
    product_id                VARCHAR(255),
    base_expires_at           DATETIME(6),
    target_expires_at         DATETIME(6),
    attempt_count             INT NOT NULL DEFAULT 0,
    attempted_at              DATETIME(6),
    held_at                   DATETIME(6),
    apple_transaction_id      VARCHAR(64),
    apple_offer_accepted_at   DATETIME(6),
    ends_at                   DATETIME(6),
    granted_at                DATETIME(6),
    created_at                DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,
    updated_at                DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_invite_rewards PRIMARY KEY (id),
    CONSTRAINT uk_invite_rewards_user_id UNIQUE (user_id),
    CONSTRAINT uk_invite_rewards_di_hash UNIQUE (di_hash(255)),
    CONSTRAINT fk_invite_rewards_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_invite_rewards_status (status)
) ENGINE = INNODB;
