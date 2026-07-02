CREATE TABLE persona_elements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,
    kind            TEXT NOT NULL,
    explanation     TEXT[] NOT NULL,
    crated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_persona_elements PRIMARY KEY (id),
    CONSTRAINT fk_persona_elements_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_persona_elements_user_id_kind UNIQUE (user_id, kind)
);
