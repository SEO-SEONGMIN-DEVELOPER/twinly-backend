CREATE TYPE DISCLOSURE_FIELD AS ENUM (
    'AFFILIATION',
    'BIRTH_DATE'
);

CREATE TABLE disclosure_agreements (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    field       DISCLOSURE_FIELD NOT NULL,
    agreed_at   TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_disclosure_agreements PRIMARY KEY (id),
    CONSTRAINT fk_disclosure_agreements_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_disclosure_agreements_user_id_field UNIQUE (user_id, field)
);