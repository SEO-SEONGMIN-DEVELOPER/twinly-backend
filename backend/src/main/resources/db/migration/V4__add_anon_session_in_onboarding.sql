CREATE TABLE anon_sessions (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY,
    token                     UUID NOT NULL,
    expires_at                TIMESTAMPTZ NOT NULL,
    nickname                  TEXT,
    family_name               TEXT,
    given_name                TEXT,
    gender                    GENDER,
    affiliation               TEXT,
    affiliation_number        TEXT,
    experience                TEXT,
    birth_date                TEXT,
    height                    TEXT,
    phone_number              TEXT,
    email                     TEXT,
    photo_key                  TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_anon_sessions PRIMARY KEY (id),
    CONSTRAINT uk_anon_sessions_token UNIQUE (token)
);