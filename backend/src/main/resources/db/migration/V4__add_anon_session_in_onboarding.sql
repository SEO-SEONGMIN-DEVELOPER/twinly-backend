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
    birth_date                TEXT,
    phone_number              TEXT,
    email                     TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_anon_sessions PRIMARY KEY (id),
    CONSTRAINT uk_anon_sessions_nickname UNIQUE (nickname),
    CONSTRAINT uk_anon_sessions_token UNIQUE (token)
);

CREATE TABLE anon_session_photos (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    anon_session_id     BIGINT NOT NULL,
    type                PHOTO_TYPE NOT NULL,
    key                 TEXT NOT NULL,
    x_pos               INTEGER,
    y_pos               INTEGER,
    width               INTEGER,
    height              INTEGER,
    uploaded_at         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_anon_session_photos PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_photos_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions (id),
    CONSTRAINT uk_anon_session_photos_anon_session_id_type UNIQUE (anon_session_id, type)
);

CREATE TABLE anon_session_agreements (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    anon_session_id     BIGINT NOT NULL,
    policy_id           BIGINT NOT NULL,
    agreed_at           TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_anon_session_agreements PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_agreements_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions (id),
    CONSTRAINT fk_anon_session_agreements_policy_id FOREIGN KEY (policy_id) REFERENCES policies (id)
);