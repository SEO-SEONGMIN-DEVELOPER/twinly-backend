CREATE TABLE chat_room_openings (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_a_id     BIGINT NOT NULL,
    user_b_id     BIGINT NOT NULL,
    scheduled_at  DATETIME(6) NOT NULL,
    opened_at     DATETIME(6),
    created_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_chat_room_openings PRIMARY KEY (id),
    CONSTRAINT fk_chat_room_openings_user_a_id FOREIGN KEY (user_a_id) REFERENCES users(id),
    CONSTRAINT fk_chat_room_openings_user_b_id FOREIGN KEY (user_b_id) REFERENCES users(id),
    CONSTRAINT uk_chat_room_openings_user_a_id_user_b_id UNIQUE (user_a_id, user_b_id),
    CONSTRAINT ck_chat_room_openings_user_order CHECK ((user_a_id < user_b_id))
) ENGINE = INNODB;

CREATE INDEX ix_chat_room_openings_opened_at_scheduled_at ON chat_room_openings (opened_at, scheduled_at);
