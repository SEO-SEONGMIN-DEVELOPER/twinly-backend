ALTER TABLE revenue_cat_events
    ADD COLUMN type         VARCHAR(64) NOT NULL AFTER event_id,
    ADD COLUMN user_id      BIGINT      NULL AFTER type,
    ADD COLUMN environment  VARCHAR(32) NULL AFTER user_id,
    ADD COLUMN completed_at DATETIME(6) NULL,
    ADD CONSTRAINT fk_revenue_cat_events_user_id FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE revenue_cat_events
    CHANGE COLUMN processed_at received_at DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL;

CREATE INDEX ix_revenue_cat_events_received_at_type ON revenue_cat_events (received_at, type);
