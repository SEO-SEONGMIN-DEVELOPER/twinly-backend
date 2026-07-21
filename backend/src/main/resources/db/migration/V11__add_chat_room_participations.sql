CREATE TABLE chat_room_participations (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    room_id             BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    entry_agreed_at     TIMESTAMPTZ,
    is_favorited        BOOLEAN NOT NULL DEFAULT FALSE,
    is_hidden           BOOLEAN NOT NULL DEFAULT FALSE,
    left_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_chat_room_participations PRIMARY KEY (id),
    CONSTRAINT fk_chat_room_participations_room_id FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_chat_room_participations_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_chat_room_participations_room_id_user_id UNIQUE (room_id, user_id)
);