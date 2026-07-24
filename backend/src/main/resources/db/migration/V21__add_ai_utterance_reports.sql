CREATE TABLE ai_utterance_reports (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    reported_user_id    BIGINT NOT NULL,
    scene_id            BIGINT NOT NULL,
    utterance_text      TEXT NOT NULL,
    reason              TEXT NOT NULL,
    status              REPORT_STATUS NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_ai_utterance_reports PRIMARY KEY (id),
    CONSTRAINT fk_ai_utterance_reports_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_utterance_reports_reported_user_id FOREIGN KEY (reported_user_id) REFERENCES users (id)
);
