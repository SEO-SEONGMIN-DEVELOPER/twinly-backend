CREATE TYPE DIMENSION_TYPE AS ENUM (
    'OPENNESS',
    'CONSCIOUSNESS',
    'EXTRAVERSION',
    'AGREEABLENESS',
    'NEUROTICISM',
    'LIFE_STYLE',
    'CONFLICT_STYLE',
    'COMMUNICATION_STYLE',
    'INTERESTS',
    'DETAIL'
);

CREATE TABLE persona_elements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    anon_session_id BIGINT,
    user_id         BIGINT,
    dimension       DIMENSION_TYPE NOT NULL,
    explanation     TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_persona_elements PRIMARY KEY (id),
    CONSTRAINT fk_persona_elements_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions (id),
    CONSTRAINT fk_persona_elements_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);
