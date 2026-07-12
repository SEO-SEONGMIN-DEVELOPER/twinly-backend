CREATE TABLE anon_session_persona_elements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    anon_session_id BIGINT NOT NULL,
    dimension       PERSONA_DIMENSION NOT NULL,
    explanation     TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_anon_session_persona_elements PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_persona_elements_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions (id)
);

CREATE INDEX ix_anon_session_persona_elements_anon_session_id ON anon_session_persona_elements (anon_session_id);