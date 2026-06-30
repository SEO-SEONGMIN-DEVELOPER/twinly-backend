ALTER TABLE users
    DROP COLUMN onboarding_status;

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
    semester                  INT,
    birth_date                TEXT,
    height                    TEXT,
    phone_number              TEXT,
    email                     TEXT,
    onboarding_status         ONBOARDING_STATUS_TYPE,

    CONSTRAINT pk_anon_sessions PRIMARY KEY (id),
    CONSTRAINT uk_anon_sessions_token UNIQUE (token)
);