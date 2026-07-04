CREATE TYPE SURVEY_OPTION_NAME AS ENUM (
    'A',
    'B'
);

CREATE TABLE survey_answers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    anon_session_id BIGINT NOT NULL,
    q_id            INT NOT NULL,
    option_name     SURVEY_OPTION_NAME,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_survey_answers PRIMARY KEY (id),
    CONSTRAINT fk_survey_answers_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions (id),
    CONSTRAINT uk_survey_answers_anon_session_id_q_id UNIQUE (anon_session_id, q_id)
);