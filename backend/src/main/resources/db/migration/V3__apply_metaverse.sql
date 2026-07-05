DROP TABLE question_provisions;
DROP TABLE question_answers;
DROP TABLE questions;

DROP TABLE events;

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE scenes (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    date                DATE,
    started_at          TIMESTAMP NOT NULL,
    ended_at            TIMESTAMP NOT NULL,
    partner_user_id     BIGINT NOT NULL,
    contents            JSON NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_scenes PRIMARY KEY (id),
    CONSTRAINT fk_scenes_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_scenes_partner_user_id FOREIGN KEY (partner_user_id) REFERENCES users (id),
    CONSTRAINT ex_scenes_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        partner_user_id WITH =,
        tsrange(started_at, ended_at) WITH &&
    )
);

CREATE TABLE questions (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id                 BIGINT NOT NULL,
    date                    DATE NOT NULL,
    contents                JSON NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_questions PRIMARY KEY (id),
    CONSTRAINT fk_questions_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE relationships (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,
    date            DATE NOT NULL,
    partner_id      BIGINT NOT NULL,
    rapport         INT NOT NULL,
    partner_model   TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_relationships PRIMARY KEY (id),
    CONSTRAINT fk_relationships_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_relationships_partner_id FOREIGN KEY (partner_id) REFERENCES users (id),
    CONSTRAINT uk_relationships_user_id_partner_id_date UNIQUE (user_id, partner_id, date)
);