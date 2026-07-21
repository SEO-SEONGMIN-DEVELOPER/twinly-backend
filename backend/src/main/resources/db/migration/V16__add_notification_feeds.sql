CREATE TYPE APP_NOTIFICATION_FEED_TYPE AS ENUM (
    'FRIEND',
    'MATCH',
    'CHAT_READY'
);

CREATE TYPE APP_NOTIFICATION_FEED_TARGET_KIND_TYPE AS ENUM (
    'PROFILE',
    'CHAT'
);

CREATE TABLE app_notification_feeds (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id              BIGINT NOT NULL,
    title                TEXT NOT NULL,
    body                 TEXT NOT NULL,
    type                 APP_NOTIFICATION_FEED_TYPE NOT NULL,
    target_kind          APP_NOTIFICATION_FEED_TARGET_KIND_TYPE NOT NULL,
    target_user_id       BIGINT,
    target_chat_room_id  BIGINT,
    read_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_notification_feeds PRIMARY KEY (id),
    CONSTRAINT fk_notification_feeds_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_feeds_target_user_id FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_feeds_target_chat_room_id FOREIGN KEY (target_chat_room_id) REFERENCES chat_rooms (id),
    CONSTRAINT ck_notification_feeds_target_kind CHECK (
        (target_kind = 'PROFILE' AND target_user_id IS NOT NULL AND target_chat_room_id IS NULL)
        OR
        (target_kind = 'CHAT' AND target_chat_room_id IS NOT NULL AND target_user_id IS NULL)
    )
);

CREATE INDEX ix_notification_feeds_notification_id ON app_notification_feeds (user_id);
