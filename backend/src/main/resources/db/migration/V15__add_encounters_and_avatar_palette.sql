CREATE TYPE AVATAR_PALETTE_COLOR AS ENUM (
    'COLOR1',
    'COLOR2'
);

ALTER TABLE users ADD COLUMN avatar_palette_color AVATAR_PALETTE_COLOR;

CREATE TABLE encounters (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_a_id   BIGINT NOT NULL,
    user_b_id   BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_encounters PRIMARY KEY (id),
    CONSTRAINT fk_encounters_user_a_id FOREIGN KEY (user_a_id) REFERENCES users (id),
    CONSTRAINT fk_encounters_user_b_id FOREIGN KEY (user_b_id) REFERENCES users (id),
    CONSTRAINT uk_encounters_user_a_id_user_b_id UNIQUE (user_a_id, user_b_id),
    CONSTRAINT ck_encounters_user_order CHECK (user_a_id < user_b_id)
);

CREATE TABLE encounter_preferences (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    encounter_id  BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    is_favorited  BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_encounter_preferences PRIMARY KEY (id),
    CONSTRAINT fk_encounter_preferences_encounter_id FOREIGN KEY (encounter_id) REFERENCES encounters (id),
    CONSTRAINT fk_encounter_preferences_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_encounter_preferences_encounter_id_user_id UNIQUE (encounter_id, user_id)
);

CREATE INDEX ix_encounter_preferences_user_id ON encounter_preferences (user_id);
