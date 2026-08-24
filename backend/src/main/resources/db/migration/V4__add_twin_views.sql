CREATE TABLE twin_views (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    target_user_id  BIGINT NOT NULL,
    viewer_user_id  BIGINT NOT NULL,
    kind            ENUM ('PROFILE','EVENT') NOT NULL,
    viewed_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,
    created_at      DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_twin_views PRIMARY KEY (id),
    CONSTRAINT fk_twin_views_target_user_id FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_twin_views_viewer_user_id FOREIGN KEY (viewer_user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE INDEX ix_twin_views_viewed_at ON twin_views (viewed_at, target_user_id, viewer_user_id);

ALTER TABLE app_notification_feeds
    MODIFY type ENUM ('FRIEND','MATCH','TWIN_VIEW') NOT NULL;
