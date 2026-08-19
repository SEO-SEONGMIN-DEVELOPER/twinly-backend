-- MySQL baseline schema (converted from Postgres final schema)

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE agreements (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    policy_id   BIGINT NOT NULL,
    agreed_at   DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6),
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_agreements PRIMARY KEY (id),
    CONSTRAINT fk_agreements_policy_id FOREIGN KEY (policy_id) REFERENCES policies(id),
    CONSTRAINT fk_agreements_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE TABLE ai_chats (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    sender      ENUM ('AI','USER') NOT NULL,
    message     TEXT NOT NULL,
    turn_index  INTEGER DEFAULT 0 NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_ai_chats PRIMARY KEY (id),
    CONSTRAINT fk_ai_chats_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_ai_chats_user_id_sender_turn_index UNIQUE (user_id, sender, turn_index)
) ENGINE = INNODB;

CREATE TABLE ai_utterance_reports (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    user_id           BIGINT NOT NULL,
    reported_user_id  BIGINT NOT NULL,
    scene_id          BIGINT NOT NULL,
    utterance_text    TEXT NOT NULL,
    reason            TEXT NOT NULL,
    status            ENUM ('PENDING','IN_REVIEW','REJECTED','RESOLVED') NOT NULL,
    created_at        DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_ai_utterance_reports PRIMARY KEY (id),
    CONSTRAINT fk_ai_utterance_reports_reported_user_id FOREIGN KEY (reported_user_id) REFERENCES users(id),
    CONSTRAINT fk_ai_utterance_reports_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE TABLE anon_session_agreements (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    anon_session_id  BIGINT NOT NULL,
    policy_id        BIGINT NOT NULL,
    agreed_at        DATETIME(6) NOT NULL,
    revoked_at       DATETIME(6),
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_session_agreements PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_agreements_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id),
    CONSTRAINT fk_anon_session_agreements_policy_id FOREIGN KEY (policy_id) REFERENCES policies(id)
) ENGINE = INNODB;

CREATE TABLE anon_session_ai_chats (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    anon_session_id  BIGINT NOT NULL,
    sender           ENUM ('AI','USER') NOT NULL,
    message          TEXT NOT NULL,
    turn_index       INTEGER DEFAULT 0 NOT NULL,
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_session_ai_chats PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_ai_chats_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id),
    CONSTRAINT uk_anon_session_ai_chats_session_sender_turn_index UNIQUE (anon_session_id, sender, turn_index)
) ENGINE = INNODB;

CREATE TABLE anon_session_persona_elements (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    anon_session_id  BIGINT NOT NULL,
    dimension        ENUM ('OPENNESS','CONSCIENTIOUSNESS','EXTRAVERSION','AGREEABLENESS','NEUROTICISM','LIFE_STYLE','CONFLICT_STYLE','COMMUNICATION_STYLE','INTEREST','DETAIL') NOT NULL,
    explanation      TEXT NOT NULL,
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_session_persona_elements PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_persona_elements_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id)
) ENGINE = INNODB;

CREATE INDEX ix_anon_session_persona_elements_anon_session_id ON anon_session_persona_elements (anon_session_id);

CREATE TABLE anon_session_photos (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    anon_session_id  BIGINT NOT NULL,
    type             ENUM ('PROFILE','BACKGROUND') NOT NULL,
    `key`            TEXT NOT NULL,
    x_pos            INTEGER,
    y_pos            INTEGER,
    width            INTEGER,
    height           INTEGER,
    uploaded_at      DATETIME(6) NOT NULL,
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_session_photos PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_photos_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id),
    CONSTRAINT uk_anon_session_photos_anon_session_id_type UNIQUE (anon_session_id, type)
) ENGINE = INNODB;

CREATE TABLE anon_session_verification_sessions (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    type                ENUM ('SMS','EMAIL') NOT NULL,
    anon_session_id     BIGINT NOT NULL,
    contact             TEXT NOT NULL,
    verification_token  BINARY(16) NOT NULL,
    code                TEXT NOT NULL,
    code_expires_at     DATETIME(6) NOT NULL,
    verified_at         DATETIME(6),
    created_at          DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_session_verification_sessions PRIMARY KEY (id),
    CONSTRAINT fk_anon_session_verification_sessions_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id),
    CONSTRAINT uk_anon_session_verification_sessions_session_type UNIQUE (anon_session_id, type),
    CONSTRAINT uk_anon_session_verification_sessions_token UNIQUE (verification_token)
) ENGINE = INNODB;

CREATE TABLE anon_sessions (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    token               BINARY(16) NOT NULL,
    expires_at          DATETIME(6) NOT NULL,
    nickname            TEXT,
    family_name         TEXT,
    given_name          TEXT,
    gender              ENUM ('MALE','FEMALE'),
    organization        TEXT,
    affiliation         TEXT,
    affiliation_number  TEXT,
    birth_date          TEXT,
    phone_number        TEXT,
    phone_number_hash   TEXT,
    email               TEXT,
    email_hash          TEXT,
    created_at          DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_anon_sessions PRIMARY KEY (id),
    CONSTRAINT uk_anon_sessions_email_hash UNIQUE (email_hash(255)),
    CONSTRAINT uk_anon_sessions_nickname UNIQUE (nickname(255)),
    CONSTRAINT uk_anon_sessions_phone_number_hash UNIQUE (phone_number_hash(255)),
    CONSTRAINT uk_anon_sessions_token UNIQUE (token)
) ENGINE = INNODB;

CREATE TABLE app_notification_feeds (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    user_id              BIGINT NOT NULL,
    title                TEXT NOT NULL,
    body                 TEXT NOT NULL,
    type                 ENUM ('FRIEND','MATCH') NOT NULL,
    target_kind          ENUM ('PROFILE','CHAT') NOT NULL,
    target_user_id       BIGINT,
    target_chat_room_id  BIGINT,
    simulation_date      DATE,
    read_at              DATETIME(6),
    created_at           DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_notification_feeds PRIMARY KEY (id),
    CONSTRAINT fk_notification_feeds_target_chat_room_id FOREIGN KEY (target_chat_room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_notification_feeds_target_user_id FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_notification_feeds_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_notification_feeds_target_kind CHECK ((((target_kind = 'PROFILE') AND (target_user_id IS NOT NULL) AND (target_chat_room_id IS NULL)) OR ((target_kind = 'CHAT') AND (target_chat_room_id IS NOT NULL) AND (target_user_id IS NULL))))
) ENGINE = INNODB;

CREATE INDEX ix_notification_feeds_notification_id ON app_notification_feeds (user_id);

CREATE TABLE blocks (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    blocked_user_id  BIGINT NOT NULL,
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_blocks PRIMARY KEY (id),
    CONSTRAINT fk_blocks_blocked_user_id FOREIGN KEY (blocked_user_id) REFERENCES users(id),
    CONSTRAINT fk_blocks_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_blocks_user_id_blocked_user_id UNIQUE (user_id, blocked_user_id),
    CONSTRAINT ck_blocks_self_block_prevention CHECK ((user_id <> blocked_user_id))
) ENGINE = INNODB;

CREATE TABLE chat_room_participations (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    room_id               BIGINT NOT NULL,
    user_id               BIGINT NOT NULL,
    entry_agreed_at       DATETIME(6),
    is_favorited          BOOLEAN DEFAULT 0 NOT NULL,
    is_hidden             BOOLEAN DEFAULT 0 NOT NULL,
    left_at               DATETIME(6),
    last_read_message_id  BIGINT,
    created_at            DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_chat_room_participations PRIMARY KEY (id),
    CONSTRAINT fk_chat_room_participations_last_read_message_id FOREIGN KEY (last_read_message_id) REFERENCES chats(id),
    CONSTRAINT fk_chat_room_participations_room_id FOREIGN KEY (room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_chat_room_participations_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_chat_room_participations_room_id_user_id UNIQUE (room_id, user_id)
) ENGINE = INNODB;

CREATE TABLE chat_rooms (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    match_id      BIGINT NOT NULL,
    closed_at     DATETIME(6),
    close_reason  TEXT,
    created_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_chat_rooms PRIMARY KEY (id),
    CONSTRAINT fk_chat_rooms_match_id FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT uk_chat_rooms_match_id UNIQUE (match_id)
) ENGINE = INNODB;

CREATE TABLE chats (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    client_msg_id     TEXT NOT NULL,
    room_id           BIGINT NOT NULL,
    sender_user_id    BIGINT NOT NULL,
    receiver_user_id  BIGINT NOT NULL,
    type              ENUM ('TEXT','PHOTO','VIDEO') NOT NULL,
    message           TEXT NOT NULL,
    sent_at           DATETIME(6) NOT NULL,
    created_at        DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_chats PRIMARY KEY (id),
    CONSTRAINT fk_chats_receiver_user_id FOREIGN KEY (receiver_user_id) REFERENCES users(id),
    CONSTRAINT fk_chats_room_id FOREIGN KEY (room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_chats_sender_user_id FOREIGN KEY (sender_user_id) REFERENCES users(id),
    CONSTRAINT uk_chats_sender_user_id_client_msg_id UNIQUE (sender_user_id, client_msg_id(255))
) ENGINE = INNODB;

CREATE INDEX ix_chats_sender_user_id_receiver_user_id ON chats (sender_user_id, receiver_user_id);

CREATE TABLE connection_tickets (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    ticket           BINARY(16) NOT NULL,
    connection_type  ENUM ('WS','SSE') NOT NULL,
    expires_at       DATETIME(6) NOT NULL,
    used_at          DATETIME(6),
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_connection_tickets PRIMARY KEY (id),
    CONSTRAINT fk_connection_tickets_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_connection_tickets_ticket UNIQUE (ticket)
) ENGINE = INNODB;

CREATE TABLE devices (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    device_id     BINARY(16) NOT NULL,
    platform      ENUM ('IOS','ANDROID') NOT NULL,
    push_token    TEXT,
    created_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT fk_devices_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_devices_device_id UNIQUE (device_id)
) ENGINE = INNODB;

CREATE INDEX ix_devices_user_id ON devices (user_id);

CREATE TABLE disclosure_agreements (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    field       ENUM ('AFFILIATION','AFFILIATION_NUMBER') NOT NULL,
    agreed_at   DATETIME(6) NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_disclosure_agreements PRIMARY KEY (id),
    CONSTRAINT fk_disclosure_agreements_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_disclosure_agreements_user_id_field UNIQUE (user_id, field)
) ENGINE = INNODB;

CREATE TABLE encounter_preferences (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    encounter_id  BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    is_favorited  BOOLEAN DEFAULT 0 NOT NULL,
    created_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_encounter_preferences PRIMARY KEY (id),
    CONSTRAINT fk_encounter_preferences_encounter_id FOREIGN KEY (encounter_id) REFERENCES encounters(id),
    CONSTRAINT fk_encounter_preferences_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_encounter_preferences_encounter_id_user_id UNIQUE (encounter_id, user_id)
) ENGINE = INNODB;

CREATE INDEX ix_encounter_preferences_user_id ON encounter_preferences (user_id);

CREATE TABLE encounters (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_a_id   BIGINT NOT NULL,
    user_b_id   BIGINT NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_encounters PRIMARY KEY (id),
    CONSTRAINT fk_encounters_user_a_id FOREIGN KEY (user_a_id) REFERENCES users(id),
    CONSTRAINT fk_encounters_user_b_id FOREIGN KEY (user_b_id) REFERENCES users(id),
    CONSTRAINT uk_encounters_user_a_id_user_b_id UNIQUE (user_a_id, user_b_id),
    CONSTRAINT ck_encounters_user_order CHECK ((user_a_id < user_b_id))
) ENGINE = INNODB;

CREATE TABLE matches (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_a_id   BIGINT NOT NULL,
    user_b_id   BIGINT NOT NULL,
    season_id   BIGINT NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_matches PRIMARY KEY (id),
    CONSTRAINT fk_matches_season_id FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_matches_user_a_id FOREIGN KEY (user_a_id) REFERENCES users(id),
    CONSTRAINT fk_matches_user_b_id FOREIGN KEY (user_b_id) REFERENCES users(id),
    CONSTRAINT uk_matches_user_a_id_user_b_id UNIQUE (user_a_id, user_b_id),
    CONSTRAINT ck_matches_user_order CHECK ((user_a_id < user_b_id))
) ENGINE = INNODB;

CREATE TABLE notification_settings (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    channel     ENUM ('PUSH','SMS','EMAIL') NOT NULL,
    type        ENUM ('EVENT','CHAT','MARKETING') NOT NULL,
    enabled     BOOLEAN DEFAULT 1 NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_notification_settings PRIMARY KEY (id),
    CONSTRAINT fk_notification_settings_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_notification_settings_user_id_channel_id_type UNIQUE (user_id, channel, type)
) ENGINE = INNODB;

CREATE TABLE notifications (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    type        ENUM ('EVENT','CHAT','MARKETING') NOT NULL,
    title       TEXT NOT NULL,
    body        TEXT NOT NULL,
    read_at     DATETIME(6),
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE INDEX ix_notifications_user_id ON notifications (user_id);

CREATE TABLE organization_affiliations (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    organization_id  BIGINT NOT NULL,
    name             VARCHAR(100) NOT NULL,

    CONSTRAINT pk_organization_affiliations PRIMARY KEY (id),
    CONSTRAINT fk_organization_affiliations_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT uk_organization_affiliations_organization_id_name UNIQUE (organization_id, name)
) ENGINE = INNODB;

CREATE TABLE organization_domains (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    organization_id  BIGINT NOT NULL,
    domain           VARCHAR(255) NOT NULL,

    CONSTRAINT pk_organization_domains PRIMARY KEY (id),
    CONSTRAINT fk_organization_domains_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT uk_organization_domains_domain UNIQUE (domain)
) ENGINE = INNODB;

CREATE TABLE organizations (
    id    BIGINT NOT NULL AUTO_INCREMENT,
    name  VARCHAR(100) NOT NULL,

    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT uk_organizations_name UNIQUE (name)
) ENGINE = INNODB;

CREATE TABLE persona_elements (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    dimension    ENUM ('OPENNESS','CONSCIENTIOUSNESS','EXTRAVERSION','AGREEABLENESS','NEUROTICISM','LIFE_STYLE','CONFLICT_STYLE','COMMUNICATION_STYLE','INTEREST','DETAIL') NOT NULL,
    explanation  TEXT NOT NULL,
    created_at   DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_persona_elements PRIMARY KEY (id),
    CONSTRAINT fk_persona_elements_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE INDEX ix_persona_elements_user_id ON persona_elements (user_id);

CREATE TABLE photos (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    type          ENUM ('PROFILE','BACKGROUND') NOT NULL,
    `key`         TEXT NOT NULL,
    thumbnail_key TEXT,
    x_pos         INTEGER,
    y_pos         INTEGER,
    width         INTEGER,
    height        INTEGER,
    uploaded_at   DATETIME(6) NOT NULL,
    created_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_photos PRIMARY KEY (id),
    CONSTRAINT fk_photos_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_photos_user_id_type UNIQUE (user_id, type)
) ENGINE = INNODB;

CREATE TABLE policies (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    policy_name_id  BIGINT NOT NULL,
    version         VARCHAR(20) NOT NULL,
    `key`           TEXT NOT NULL,
    is_required     BOOLEAN DEFAULT 1 NOT NULL,
    effective_at    DATETIME(6),
    created_at      DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_policies PRIMARY KEY (id),
    CONSTRAINT fk_policies_policy_name_id FOREIGN KEY (policy_name_id) REFERENCES policy_names(id),
    CONSTRAINT uk_policies_policy_name_id_version UNIQUE (policy_name_id, version)
) ENGINE = INNODB;

CREATE TABLE policy_names (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    name                TEXT NOT NULL,
    identifier          VARCHAR(100) NOT NULL,
    kind                ENUM ('ONBOARDING','PARALLEL_ENTRY') DEFAULT 'ONBOARDING' NOT NULL,
    requires_agreement  BOOLEAN DEFAULT 1 NOT NULL,
    is_deprecated       BOOLEAN DEFAULT 0 NOT NULL,

    CONSTRAINT pk_policy_names PRIMARY KEY (id),
    CONSTRAINT uk_policy_names_identifier UNIQUE (identifier)
) ENGINE = INNODB;

CREATE TABLE question_partners (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    question_id  BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    created_at   DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_question_partners PRIMARY KEY (id),
    CONSTRAINT fk_question_partners_question_id FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT fk_question_partners_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_question_partners_question_id_user_id UNIQUE (question_id, user_id)
) ENGINE = INNODB;

CREATE TABLE questions (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    `date`       DATE NOT NULL,
    version      TEXT NOT NULL,
    `time`       DATETIME(6) NOT NULL,
    type         ENUM ('PROMISE','PERSONA') NOT NULL,
    `text`       TEXT NOT NULL,
    options      JSON NOT NULL,
    choice       TEXT,
    answered_at  DATETIME(6),
    is_skipped   BOOLEAN DEFAULT 0 NOT NULL,
    created_at   DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_questions PRIMARY KEY (id),
    CONSTRAINT fk_questions_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE INDEX ix_questions_user_id_date ON questions (user_id, date);

CREATE TABLE refresh_tokens (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    token_hash  TEXT NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash(255))
) ENGINE = INNODB;

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE relationships (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    `date`           DATE NOT NULL,
    version          TEXT NOT NULL,
    partner_user_id  BIGINT NOT NULL,
    intimacy         INTEGER NOT NULL,
    partner_model    TEXT NOT NULL,
    update_time      DATETIME(6) NOT NULL,
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_relationships PRIMARY KEY (id),
    CONSTRAINT fk_relationships_partner_user_id FOREIGN KEY (partner_user_id) REFERENCES users(id),
    CONSTRAINT fk_relationships_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_relationships_user_id_partner_user_id_date UNIQUE (user_id, date, partner_user_id)
) ENGINE = INNODB;

CREATE TABLE reports (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    user_id           BIGINT NOT NULL,
    reported_user_id  BIGINT NOT NULL,
    reason            ENUM ('SPAM','INAPPROPRIATE_PHOTO','FRAUD_SUSPECTED','HARASSMENT','THREAT','OTHER') NOT NULL,
    detail            TEXT,
    status            ENUM ('PENDING','IN_REVIEW','REJECTED','RESOLVED') NOT NULL,
    created_at        DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_reported_user_id FOREIGN KEY (reported_user_id) REFERENCES users(id),
    CONSTRAINT fk_reports_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE TABLE scene_partners (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    scene_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_scene_partners PRIMARY KEY (id),
    CONSTRAINT fk_scene_partners_scene_id FOREIGN KEY (scene_id) REFERENCES scenes(id),
    CONSTRAINT fk_scene_partners_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_scene_partners_scene_id_user_id UNIQUE (scene_id, user_id)
) ENGINE = INNODB;

CREATE TABLE scenes (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    `date`      DATE NOT NULL,
    version     TEXT NOT NULL,
    place       TEXT NOT NULL,
    starts_at   DATETIME(6) NOT NULL,
    ends_at     DATETIME(6) NOT NULL,
    type        ENUM ('ACTION','DIALOGUE') NOT NULL,
    narration   TEXT,
    mind        TEXT,
    `lines`     JSON,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_scenes PRIMARY KEY (id),
    CONSTRAINT fk_scenes_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE INDEX ix_scenes_user_id_date ON scenes (user_id, date);

CREATE TABLE season_participations (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    season_id           BIGINT NOT NULL,
    participated_in_at  DATETIME(6) NOT NULL,
    created_at          DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_season_participations PRIMARY KEY (id),
    CONSTRAINT fk_season_participations_season_id FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_season_participations_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_season_participations_user_id_season_id UNIQUE (user_id, season_id)
) ENGINE = INNODB;

CREATE TABLE seasons (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    started_at  DATETIME(6) NOT NULL,
    ended_at    DATETIME(6) NOT NULL,
    is_active   BOOLEAN DEFAULT 0 NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_seasons PRIMARY KEY (id)
) ENGINE = INNODB;

CREATE TABLE shedlock (
    name        VARCHAR(64) NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,

    CONSTRAINT pk_shedlock PRIMARY KEY (name)
) ENGINE = INNODB;

CREATE TABLE survey_answers (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    anon_session_id  BIGINT NOT NULL,
    question_id      INTEGER NOT NULL,
    option_name      ENUM ('A','B'),
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_survey_answers PRIMARY KEY (id),
    CONSTRAINT fk_survey_answers_anon_session_id FOREIGN KEY (anon_session_id) REFERENCES anon_sessions(id),
    CONSTRAINT uk_survey_answers_anon_session_id_question_id UNIQUE (anon_session_id, question_id)
) ENGINE = INNODB;

CREATE TABLE users (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    nickname                 TEXT,
    family_name              TEXT,
    family_name_hash         TEXT,
    given_name               TEXT,
    given_name_hash          TEXT,
    gender                   ENUM ('MALE','FEMALE') NOT NULL,
    organization             TEXT NOT NULL,
    organization_hash        TEXT NOT NULL,
    affiliation              TEXT NOT NULL,
    affiliation_hash         TEXT NOT NULL,
    affiliation_number       TEXT,
    affiliation_number_hash  TEXT,
    birth_date               TEXT NOT NULL,
    birth_date_hash          TEXT,
    phone_number             TEXT,
    phone_number_hash        TEXT,
    email                    TEXT,
    email_hash               TEXT,
    withdrawal_requested_at  DATETIME(6),
    withdrawal_scheduled_at  DATETIME(6),
    deleted_at               DATETIME(6),
    created_at               DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email_hash UNIQUE (email_hash(255)),
    CONSTRAINT uk_users_nickname UNIQUE (nickname(255)),
    CONSTRAINT uk_users_phone_number_hash UNIQUE (phone_number_hash(255))
) ENGINE = INNODB;

CREATE TABLE verification_sessions (
    id                         BIGINT NOT NULL AUTO_INCREMENT,
    type                       ENUM ('SMS','EMAIL') NOT NULL,
    contact                    TEXT NOT NULL,
    verification_token         BINARY(16) NOT NULL,
    code                       TEXT NOT NULL,
    code_expires_at            DATETIME(6) NOT NULL,
    verified_token             BINARY(16),
    verified_token_expires_at  DATETIME(6),
    verified_at                DATETIME(6),
    created_at                 DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_verification_sessions PRIMARY KEY (id),
    CONSTRAINT uk_verification_sessions_verification_token UNIQUE (verification_token),
    CONSTRAINT uk_verification_sessions_verified_token UNIQUE (verified_token)
) ENGINE = INNODB;

CREATE TABLE verifications (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    type         ENUM ('SMS','EMAIL') NOT NULL,
    verified_at  DATETIME(6) NOT NULL,
    created_at   DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_verifications PRIMARY KEY (id),
    CONSTRAINT fk_verifications_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

SET FOREIGN_KEY_CHECKS = 1;
