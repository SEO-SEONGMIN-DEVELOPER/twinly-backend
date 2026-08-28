CREATE TABLE user_entitlements (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    entitlement  VARCHAR(64) NOT NULL,
    expires_at   DATETIME(6),
    synced_at    DATETIME(6) NOT NULL,
    created_at   DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_user_entitlements PRIMARY KEY (id),
    CONSTRAINT uk_user_entitlements_user_id_entitlement UNIQUE (user_id, entitlement),
    CONSTRAINT fk_user_entitlements_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE TABLE user_balances (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    asset       VARCHAR(64) NOT NULL,
    amount      INT NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_user_balances PRIMARY KEY (id),
    CONSTRAINT uk_user_balances_user_id_asset UNIQUE (user_id, asset),
    CONSTRAINT fk_user_balances_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE TABLE revenue_cat_events (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    event_id      VARCHAR(64) NOT NULL,
    processed_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_revenue_cat_events PRIMARY KEY (id),
    CONSTRAINT uk_revenue_cat_events_event_id UNIQUE (event_id)
) ENGINE = INNODB;
