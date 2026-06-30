CREATE TYPE GENDER AS ENUM (
    'MALE',
    'FEMALE'
);

CREATE TYPE ONBOARDING_STATUS_TYPE AS ENUM ();

CREATE TABLE users (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY,
    nickname                  TEXT NOT NULL,
    name                      TEXT NOT NULL,
    gender                    GENDER NOT NULL,
    affiliation               TEXT NOT NULL,
    affiliation_number        TEXT NOT NULL,
    semester                  INT,
    birth_date                DATE NOT NULL,
    height                    NUMERIC(4, 1) NOT NULL,
    phone_number              TEXT NOT NULL,
    email                     TEXT NOT NULL,
    onboarding_status         ONBOARDING_STATUS_TYPE NOT NULL,
    withdrawal_requested_at   TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE questions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    item        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_questions PRIMARY KEY (id)
);

CREATE TABLE question_answers (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    question_id         BIGINT NOT NULL,
    answer_option       TEXT NOT NULL,
    label               TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_question_answers PRIMARY KEY (id),
    CONSTRAINT fk_question_answers_question_id FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT uk_question_answers_question_id_answer_option UNIQUE (question_id, answer_option)
);

CREATE TABLE question_provisions (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id                 BIGINT NOT NULL,
    question_id             BIGINT NOT NULL,
    question_answer_id      BIGINT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_question_provisions PRIMARY KEY (id),
    CONSTRAINT fk_question_provisions_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_question_provisions_question_id FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fk_question_provisions_question_answer_id FOREIGN KEY (question_answer_id) REFERENCES question_answers (id),
    CONSTRAINT uk_question_provisions_user_id_question_id UNIQUE (user_id, question_id)
);

CREATE TABLE matches (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_a_id   BIGINT NOT NULL,
    user_b_id   BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_matches PRIMARY KEY (id),
    CONSTRAINT fk_matches_user_a_id FOREIGN KEY (user_a_id) REFERENCES users (id),
    CONSTRAINT fk_matches_user_b_id FOREIGN KEY (user_b_id) REFERENCES users (id),
    CONSTRAINT uk_matches_user_a_id_user_b_id UNIQUE (user_a_id, user_b_id),
    CONSTRAINT ck_matches_user_order CHECK(user_a_id < user_b_id)
);

CREATE TABLE chats (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    match_id            BIGINT NOT NULL,
    sender_user_id      BIGINT NOT NULL,
    receiver_user_id    BIGINT NOT NULL,
    message             TEXT NOT NULL,
    sent_at             TIMESTAMPTZ NOT NULL,
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_chats PRIMARY KEY (id),
    CONSTRAINT fk_chats_match_id FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_chats_sender_user_id FOREIGN KEY (sender_user_id) REFERENCES users (id),
    CONSTRAINT fk_chats_receiver_user_id FOREIGN KEY (receiver_user_id) REFERENCES users (id)
);

CREATE INDEX ix_chats_sender_user_id_receiver_user_id ON chats (sender_user_id, receiver_user_id);

CREATE TABLE blocks (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    blocker_user_id  BIGINT NOT NULL,
    blocked_user_id  BIGINT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_blocks PRIMARY KEY (id),
    CONSTRAINT fk_blocks_blocker_user_id FOREIGN KEY (blocker_user_id) REFERENCES users (id),
    CONSTRAINT fk_blocks_blocked_user_id FOREIGN KEY (blocked_user_id) REFERENCES users (id),
    CONSTRAINT uk_blocks_blocker_user_id_blocked_user_id UNIQUE (blocker_user_id, blocked_user_id),
    CONSTRAINT ck_blocks_self_block_prevention CHECK(blocker_user_id != blocked_user_id)
);

CREATE TABLE reports (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    reporter_user_id    BIGINT NOT NULL,
    reported_user_id    BIGINT NOT NULL,
    reason              TEXT NOT NULL,
    status              TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter_user_id FOREIGN KEY (reporter_user_id) REFERENCES users (id),
    CONSTRAINT fk_reports_reported_user_id FOREIGN KEY (reported_user_id) REFERENCES users (id)
);

CREATE TABLE events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    day         DATE NOT NULL,
    content     JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_events_user_id_day UNIQUE (user_id, day)
);

CREATE TYPE PHOTO_TYPE AS ENUM (
    'PROFILE',
    'BACKGROUND'
);

CREATE TABLE photos (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id      BIGINT NOT NULL,
    type         PHOTO_TYPE NOT NULL,
    url          TEXT NOT NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL,
    is_current   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_photos PRIMARY KEY (id),
    CONSTRAINT fk_photos_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uk_photos_user_id_type ON photos (user_id, type) WHERE is_current = true;

CREATE TYPE VERIFICATION_TYPE AS ENUM (
    'SMS',
    'EMAIL'
);

CREATE TABLE verifications (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id      BIGINT NOT NULL,
    type         VERIFICATION_TYPE NOT NULL,
    verified_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_verifications PRIMARY KEY (id),
    CONSTRAINT fk_verifications_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TYPE AGREEMENT_TYPE AS ENUM (
    'TERMS_OF_SERVICE',
    'PRIVACY_POLICY',
    'PERSONAL_INFO_COLLECTION_USE_CONSENT',
    'YOUTH_PROTECTION_POLICY',
    'MARKETING_CONSENT',
    'PAID_SERVICE_REFUND_POLICY'
);

CREATE TABLE agreements (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    type        AGREEMENT_TYPE NOT NULL,
    version     TEXT NOT NULL,
    agreed_at   TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_agreements PRIMARY KEY (id),
    CONSTRAINT fk_agreements_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TYPE NOTIFICATION_CHANNEL AS ENUM (
    'PUSH',
    'SMS',
    'EMAIL'
);

CREATE TYPE NOTIFICATION_TYPE AS ENUM (
    'UNIVERSE_START',
    'UNIVERSE_EVENT',
    'UNIVERSE_REVEAL',
    'CHAT',
    'MARKETING'
);

CREATE TABLE notification_settings (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    channel     NOTIFICATION_CHANNEL NOT NULL,
    type        NOTIFICATION_TYPE NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_notification_settings PRIMARY KEY (id),
    CONSTRAINT fk_notification_settings_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_notification_settings_user_id_channel_id_type UNIQUE (user_id, channel, type)
);