ALTER TABLE seasons
    ADD COLUMN recap_created_at  DATETIME(6),
    ADD COLUMN recap_notified_at DATETIME(6);

UPDATE seasons
SET recap_created_at  = UTC_TIMESTAMP(6),
    recap_notified_at = UTC_TIMESTAMP(6)
WHERE is_active = FALSE;

CREATE TABLE season_recaps (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    season_id   BIGINT NOT NULL,
    data        JSON NOT NULL,
    created_at  DATETIME(6) NOT NULL,

    CONSTRAINT pk_season_recaps PRIMARY KEY (id),
    CONSTRAINT fk_season_recaps_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_season_recaps_season_id FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT uk_season_recaps_user_id_season_id UNIQUE (user_id, season_id)
) ENGINE = INNODB;

ALTER TABLE app_notification_feeds
    DROP CHECK ck_notification_feeds_target_kind;

ALTER TABLE app_notification_feeds
    ADD COLUMN target_season_id BIGINT,
    MODIFY type ENUM ('FRIEND','MATCH','TWIN_VIEW','ONE_TIME','NOTICE','BALANCE_GAME_ANSWERED','SCHOOL_VERIFICATION','SEASON_RECAP') NOT NULL,
    MODIFY target_kind ENUM ('PROFILE','CHAT','PEOPLE','BALANCE_GAME','SEASON_RECAP'),
    ADD CONSTRAINT fk_app_notification_feeds_target_season_id FOREIGN KEY (target_season_id) REFERENCES seasons(id);

ALTER TABLE app_notification_feeds
    ADD CONSTRAINT ck_notification_feeds_target_kind CHECK (
        ((type IN ('NOTICE','SCHOOL_VERIFICATION')) AND (target_kind IS NULL) AND (target_user_id IS NULL) AND (target_chat_room_id IS NULL) AND (target_season_id IS NULL))
        OR ((type NOT IN ('NOTICE','SCHOOL_VERIFICATION')) AND (target_kind IS NOT NULL) AND (
            ((target_kind = 'PROFILE') AND (target_user_id IS NOT NULL) AND (target_chat_room_id IS NULL) AND (target_season_id IS NULL))
            OR ((target_kind = 'CHAT') AND (target_chat_room_id IS NOT NULL) AND (target_user_id IS NULL) AND (target_season_id IS NULL))
            OR ((target_kind = 'PEOPLE') AND (target_user_id IS NULL) AND (target_chat_room_id IS NULL) AND (target_season_id IS NULL))
            OR ((target_kind = 'BALANCE_GAME') AND (target_user_id IS NOT NULL) AND (target_chat_room_id IS NULL) AND (target_season_id IS NULL))
            OR ((target_kind = 'SEASON_RECAP') AND (target_season_id IS NOT NULL) AND (target_user_id IS NULL) AND (target_chat_room_id IS NULL))))
    );
