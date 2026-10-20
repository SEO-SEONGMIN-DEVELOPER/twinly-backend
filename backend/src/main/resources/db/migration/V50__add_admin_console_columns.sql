ALTER TABLE reports
    ADD COLUMN review_note   TEXT AFTER status,
    ADD COLUMN action_note   TEXT AFTER review_note,
    ADD COLUMN processed_at  DATETIME(6) AFTER action_note;

ALTER TABLE ai_utterance_reports
    ADD COLUMN violation_kind  ENUM ('AI_UTTERANCE','USER_VIOLATION','NONE') AFTER status,
    ADD COLUMN review_note     TEXT AFTER violation_kind,
    ADD COLUMN action_note     TEXT AFTER review_note,
    ADD COLUMN processed_at    DATETIME(6) AFTER action_note;

ALTER TABLE user_feedbacks
    ADD COLUMN status  ENUM ('RECEIVED','IN_REVIEW','DONE') NOT NULL DEFAULT 'RECEIVED' AFTER app_version,
    ADD COLUMN memo    TEXT AFTER status;

ALTER TABLE user_entitlements
    ADD COLUMN store        VARCHAR(32) AFTER expires_at,
    ADD COLUMN period_type  VARCHAR(32) AFTER store;
