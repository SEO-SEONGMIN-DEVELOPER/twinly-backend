ALTER TABLE app_notification_feeds
    DROP CHECK ck_notification_feeds_target_kind;

ALTER TABLE app_notification_feeds
    MODIFY type ENUM ('FRIEND','MATCH','TWIN_VIEW','ONE_TIME','NOTICE','BALANCE_GAME_ANSWERED') NOT NULL,
    MODIFY target_kind ENUM ('PROFILE','CHAT','PEOPLE','BALANCE_GAME');

ALTER TABLE app_notification_feeds
    ADD CONSTRAINT ck_notification_feeds_target_kind CHECK (
        ((type = 'NOTICE') AND (target_kind IS NULL) AND (target_user_id IS NULL) AND (target_chat_room_id IS NULL))
        OR ((type <> 'NOTICE') AND (target_kind IS NOT NULL) AND (
            ((target_kind = 'PROFILE') AND (target_user_id IS NOT NULL) AND (target_chat_room_id IS NULL))
            OR ((target_kind = 'CHAT') AND (target_chat_room_id IS NOT NULL) AND (target_user_id IS NULL))
            OR ((target_kind = 'PEOPLE') AND (target_user_id IS NULL) AND (target_chat_room_id IS NULL))
            OR ((target_kind = 'BALANCE_GAME') AND (target_user_id IS NOT NULL) AND (target_chat_room_id IS NULL))))
    );
