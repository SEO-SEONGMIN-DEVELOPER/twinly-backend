DROP TABLE question_provisions;
DROP TABLE question_answers;
DROP TABLE questions;

DROP TABLE events;

CREATE TYPE SCENE_TYPE AS ENUM (
    'ACTION',
    'DIALOGUE'
);

CREATE TABLE scenes (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    date                DATE NOT NULL,
    version             TEXT NOT NULL,
    place               TEXT NOT NULL,
    starts_at           TIMESTAMP NOT NULL,
    ends_at             TIMESTAMP NOT NULL,
    type                SCENE_TYPE NOT NULL,
    narration           TEXT,
    mind                TEXT,
    lines               JSON,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_scenes PRIMARY KEY (id),
    CONSTRAINT fk_scenes_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ex_scenes_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        date WITH =,
        tsrange(starts_at, ends_at) WITH &&
    )
);

CREATE INDEX ix_scenes_user_id_date ON scenes (user_id, date);

CREATE TABLE scene_partners (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    scene_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_scene_partners PRIMARY KEY (id),
    CONSTRAINT fk_scene_partners_scene_id FOREIGN KEY (scene_id) REFERENCES scenes (id),
    CONSTRAINT fk_scene_partners_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_scene_partners_scene_id_user_id UNIQUE (scene_id, user_id)
);

CREATE TYPE QUESTION_TYPE AS ENUM (
    'PROMISE',
    'PERSONA'
);

CREATE TABLE questions (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id                 BIGINT NOT NULL,
    date                    DATE NOT NULL,
    time                    TIME NOT NULL,
    type                    QUESTION_TYPE NOT NULL,
    text                    TEXT NOT NULL,
    options                 TEXT[] NOT NULL,
    choice                  TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_questions PRIMARY KEY (id),
    CONSTRAINT fk_questions_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_questions_user_id_date ON questions (user_id, date);

CREATE TABLE question_partners (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    question_id     BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_question_partners PRIMARY KEY (id),
    CONSTRAINT fk_question_partners_question_id FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fk_question_partners_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_question_partners_question_id_user_id UNIQUE (question_id, user_id)
);

CREATE TABLE relationships (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    date                DATE NOT NULL,
    partner_user_id     BIGINT NOT NULL,
    update_at           TIMESTAMPTZ NOT NULL,
    intimacy            INT NOT NULL,
    partner_model       TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_relationships PRIMARY KEY (id),
    CONSTRAINT fk_relationships_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_relationships_partner_user_id FOREIGN KEY (partner_user_id) REFERENCES users (id),
    CONSTRAINT uk_relationships_user_id_partner_user_id_date UNIQUE (user_id, date, partner_user_id)
);