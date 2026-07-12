CREATE TYPE PERSONA_DIMENSION AS ENUM (
    'OPENNESS',
    'CONSCIENTIOUSNESS',
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
    user_id         BIGINT NOT NULL,
    dimension       PERSONA_DIMENSION NOT NULL,
    explanation     TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_persona_elements PRIMARY KEY (id),
    CONSTRAINT fk_persona_elements_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_persona_elements_user_id ON persona_elements (user_id);