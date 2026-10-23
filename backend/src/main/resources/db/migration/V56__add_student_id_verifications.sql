ALTER TABLE users
    ADD COLUMN school_verification_status ENUM ('APPROVED','PENDING','REJECTED') NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN school_verification_method ENUM ('EMAIL','STUDENT_ID') NOT NULL DEFAULT 'EMAIL';

CREATE TABLE anon_session_student_id_submissions (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    submission_id     BINARY(16) NOT NULL,
    anon_session_id   BIGINT NOT NULL,
    organization      TEXT NOT NULL,
    upload_key        VARCHAR(255) NOT NULL,
    review_key        VARCHAR(255) NOT NULL,
    submitted_at      DATETIME(6) NOT NULL,
    replaced_at       DATETIME(6),
    photo_deleted_at  DATETIME(6),
    created_at        DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_session_student_id_submissions PRIMARY KEY (id),
    CONSTRAINT uk_anon_session_student_id_submissions_submission_id UNIQUE (submission_id),
    CONSTRAINT uk_anon_session_student_id_submissions_upload_key UNIQUE (upload_key),
    CONSTRAINT fk_anon_session_student_id_submissions_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id)
) ENGINE = INNODB;

CREATE TABLE student_id_submissions (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    submission_id     BINARY(16) NOT NULL,
    user_id           BIGINT NOT NULL,
    organization      TEXT NOT NULL,
    upload_key        VARCHAR(255) NOT NULL,
    review_key        VARCHAR(255) NOT NULL,
    status            ENUM ('PENDING','APPROVED','REJECTED','REPLACED','CANCELED') NOT NULL,
    reject_reason     ENUM ('BLURRY','NAME_MISMATCH','SCHOOL_MISMATCH','INVALID_DOCUMENT','RRN_VISIBLE'),
    reviewer          VARCHAR(100),
    submitted_at      DATETIME(6) NOT NULL,
    decided_at        DATETIME(6),
    photo_deleted_at  DATETIME(6),
    created_at        DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_student_id_submissions PRIMARY KEY (id),
    CONSTRAINT uk_student_id_submissions_submission_id UNIQUE (submission_id),
    CONSTRAINT uk_student_id_submissions_upload_key UNIQUE (upload_key),
    CONSTRAINT fk_student_id_submissions_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_student_id_submissions_reject_reason CHECK ((status = 'REJECTED') = (reject_reason IS NOT NULL))
) ENGINE = INNODB;

CREATE INDEX ix_student_id_submissions_user_id ON student_id_submissions (user_id);
CREATE INDEX ix_student_id_submissions_status_submitted_at ON student_id_submissions (status, submitted_at);
CREATE INDEX ix_student_id_submissions_photo_deleted_at_status ON student_id_submissions (photo_deleted_at, status);

ALTER TABLE app_notification_feeds
    DROP CHECK ck_notification_feeds_target_kind;

ALTER TABLE app_notification_feeds
    MODIFY type ENUM ('FRIEND','MATCH','TWIN_VIEW','ONE_TIME','NOTICE','BALANCE_GAME_ANSWERED','SCHOOL_VERIFICATION') NOT NULL;

ALTER TABLE app_notification_feeds
    ADD CONSTRAINT ck_notification_feeds_target_kind CHECK (
        ((type IN ('NOTICE','SCHOOL_VERIFICATION')) AND (target_kind IS NULL) AND (target_user_id IS NULL) AND (target_chat_room_id IS NULL))
        OR ((type NOT IN ('NOTICE','SCHOOL_VERIFICATION')) AND (target_kind IS NOT NULL) AND (
            ((target_kind = 'PROFILE') AND (target_user_id IS NOT NULL) AND (target_chat_room_id IS NULL))
            OR ((target_kind = 'CHAT') AND (target_chat_room_id IS NOT NULL) AND (target_user_id IS NULL))
            OR ((target_kind = 'PEOPLE') AND (target_user_id IS NULL) AND (target_chat_room_id IS NULL))
            OR ((target_kind = 'BALANCE_GAME') AND (target_user_id IS NOT NULL) AND (target_chat_room_id IS NULL))))
    );

CREATE INDEX ix_anon_sessions_expires_at ON anon_sessions (expires_at);
