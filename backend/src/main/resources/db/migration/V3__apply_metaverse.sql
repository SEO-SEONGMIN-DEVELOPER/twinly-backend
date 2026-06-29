DROP TABLE question_provisions;
DROP TABLE question_answers;
DROP TABLE questions;

DROP TABLE events;

CREATE TABLE lives (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    date        DATE NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_lives PRIMARY KEY (id),
    CONSTRAINT fk_lives_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_lives_user_id_date UNIQUE (user_id, date)
);

CREATE TYPE SCENE_TYPE AS ENUM (
    'ACTION',
    'DIALOGUE'
);

CREATE TABLE scenes (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    life_id             BIGINT NOT NULL,
    started_at          TIMESTAMP NOT NULL,
    ended_at            TIMESTAMP NOT NULL,
    type                SCENE_TYPE NOT NULL,
    place               TEXT NOT NULL,
    counterpart_user_id BIGINT,
    narration           TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_scenes PRIMARY KEY (id),
    CONSTRAINT fk_scenes_life_id FOREIGN KEY (life_id) REFERENCES lives (id),
    CONSTRAINT fk_scenes_conterpart_user_id FOREIGN KEY (counterpart_user_id) REFERENCES users (id),
    CONSTRAINT uk_scenes_life_id_started_at_ended_at UNIQUE (life_id, started_at, ended_at),
    CONSTRAINT ck_scenes_narration_by_type CHECK (
        (type = 'ACTION' AND narration IS NOT NULL)
        OR (type = 'DIALOGUE' AND narration IS NULL)
    )
);

CREATE TYPE DIALOGUE_LINE_TYPE AS ENUM (
    'UTTERANCE',
    'NARRATION'
);

CREATE TABLE dialogue_lines (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    scene_id            BIGINT NOT NULL,
    occured_at          TIMESTAMP NOT NULL,
    type                DIALOGUE_LINE_TYPE NOT NULL,
    speaker_id          BIGINT,
    verbal_content      TEXT,
    nonverbal_content   TEXT,
    narration           TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_dialogue_lines PRIMARY KEY (id),
    CONSTRAINT fk_dialogue_lines_scene_id FOREIGN KEY (scene_id) REFERENCES scenes (id),
    CONSTRAINT fk_dialogue_lines_speaker_id FOREIGN KEY (speaker_id) REFERENCES users (id),
    CONSTRAINT uk_dialogue_lines_scene_id_occured_at UNIQUE (scene_id, occured_at),
    CONSTRAINT ck_dialogue_lines_value_by_type CHECK (
        (type = 'UTTERANCE' AND speaker_id IS NOT NULL AND verbal_content IS NOT NULL AND narration IS NULL)
        OR (type = 'NARRATION' AND speaker_id IS NULL AND verbal_content IS NULL AND nonverbal_content IS NULL AND narration IS NOT NULL)
    )
);

CREATE TYPE QUESTION_TYPE AS ENUM (
    'UNIVERSE_INTERVENTION',
    'PERSONA_UPDATE'
);

CREATE TABLE questions (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    life_id                 BIGINT NOT NULL,
    questioned_at           TIMESTAMP NOT NULL,
    type                    QUESTION_TYPE NOT NULL,
    item                    TEXT NOT NULL,
    agent_choice_option_id  BIGINT,
    user_choice_option_id   BIGINT,
    answered_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_questions PRIMARY KEY (id),
    CONSTRAINT fk_questions_life_id FOREIGN KEY (life_id) REFERENCES lives (id)
);

CREATE INDEX ix_questions_life_id_questioned_at ON questions (life_id, questioned_at);

CREATE TABLE options (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    question_id     BIGINT NOT NULL,
    item            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_options PRIMARY KEY (id),
    CONSTRAINT fk_options_question_id FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT uk_options_question_id_item UNIQUE (question_id, item)
);

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_user_choice_option_id FOREIGN KEY (user_choice_option_id) REFERENCES options (id);

CREATE TABLE relationships (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    life_id         BIGINT NOT NULL,
    partner_id      BIGINT NOT NULL,
    rapport         INT NOT NULL,
    partner_model   TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_relationships PRIMARY KEY (id),
    CONSTRAINT fk_relationships_life_id FOREIGN KEY (life_id) REFERENCES lives (id),
    CONSTRAINT fk_relationships_partner_id FOREIGN KEY (partner_id) REFERENCES users (id),
    CONSTRAINT uk_relationships_life_id_partner_id UNIQUE (life_id, partner_id)
);