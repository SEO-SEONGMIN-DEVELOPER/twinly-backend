CREATE TYPE AI_CHAT_SENDER AS ENUM (
    'AI',
    'USER'
);

CREATE TABLE ai_chats (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    anon_session_id BIGINT NOT NULL,
    sender          AI_CHAT_SENDER NOT NULL,
    message         TEXT NOT NULL,
    turn_index      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_ai_chats PRIMARY KEY (id),
    CONSTRAINT fk_ai_chats_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions (id),
    CONSTRAINT uk_ai_chats_anon_session_id_sender_turn_index UNIQUE (anon_session_id, sender, turn_index)
);