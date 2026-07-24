CREATE TABLE anon_session_verification_sessions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    type                VERIFICATION_TYPE NOT NULL,
    anon_session_id     BIGINT NOT NULL,
    contact             TEXT NOT NULL,
    verification_token  UUID NOT NULL,
    code                TEXT NOT NULL,
    code_expires_at     TIMESTAMPTZ NOT NULL,
    verified_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_anon_session_verification_sessions PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_verification_sessions_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions (id),
    CONSTRAINT uk_anon_session_verification_sessions_token UNIQUE (verification_token),
    CONSTRAINT uk_anon_session_verification_sessions_session_type UNIQUE (anon_session_id, type)
);
