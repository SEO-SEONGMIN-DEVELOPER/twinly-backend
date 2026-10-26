CREATE TABLE twin_updates (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    update_id            BINARY(16) NOT NULL,
    user_id              BIGINT NOT NULL,
    status               ENUM ('IN_PROGRESS','COMPLETED','DISCARDED') NOT NULL,
    completed_at         DATETIME(6),
    created_at           DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,
    in_progress_user_id  BIGINT AS (IF(status = 'IN_PROGRESS', user_id, NULL)) STORED,

    CONSTRAINT pk_twin_updates PRIMARY KEY (id),
    CONSTRAINT uk_twin_updates_update_id UNIQUE (update_id),
    CONSTRAINT uk_twin_updates_in_progress_user_id UNIQUE (in_progress_user_id),
    CONSTRAINT fk_twin_updates_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_twin_updates_completed_at CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
) ENGINE = INNODB;

CREATE INDEX ix_twin_updates_user_id_status_completed_at ON twin_updates (user_id, status, completed_at);

CREATE TABLE twin_update_survey_answers (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    twin_update_id  BIGINT NOT NULL,
    question_id     INTEGER NOT NULL,
    option_name     ENUM ('A','B') NOT NULL,
    created_at      DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_twin_update_survey_answers PRIMARY KEY (id),
    CONSTRAINT fk_twin_update_survey_answers_twin_update_id FOREIGN KEY (twin_update_id) REFERENCES twin_updates(id),
    CONSTRAINT uk_twin_update_survey_answers_twin_update_id_question_id UNIQUE (twin_update_id, question_id)
) ENGINE = INNODB;

CREATE TABLE twin_update_interests (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    twin_update_id  BIGINT NOT NULL,
    interest        VARCHAR(50) NOT NULL,
    created_at      DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_twin_update_interests PRIMARY KEY (id),
    CONSTRAINT fk_twin_update_interests_twin_update_id FOREIGN KEY (twin_update_id) REFERENCES twin_updates(id)
) ENGINE = INNODB;

CREATE INDEX ix_twin_update_interests_twin_update_id ON twin_update_interests (twin_update_id);

CREATE TABLE twin_update_ai_chats (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    twin_update_id  BIGINT NOT NULL,
    sender          ENUM ('AI','USER') NOT NULL,
    message         TEXT NOT NULL,
    turn_index      INTEGER NOT NULL,
    created_at      DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_twin_update_ai_chats PRIMARY KEY (id),
    CONSTRAINT fk_twin_update_ai_chats_twin_update_id FOREIGN KEY (twin_update_id) REFERENCES twin_updates(id),
    CONSTRAINT uk_twin_update_ai_chats_twin_update_id_sender_turn_index UNIQUE (twin_update_id, sender, turn_index)
) ENGINE = INNODB;
