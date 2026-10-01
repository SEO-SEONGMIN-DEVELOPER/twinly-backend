CREATE TABLE user_feedbacks (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    type          ENUM ('WITHDRAWAL','SUGGESTION') NOT NULL,
    detail        VARCHAR(500),
    app_platform  ENUM ('IOS','ANDROID'),
    app_version   VARCHAR(32),
    created_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_user_feedbacks PRIMARY KEY (id),
    CONSTRAINT fk_user_feedbacks_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = INNODB;

CREATE TABLE user_feedback_options (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    feedback_id  BIGINT NOT NULL,
    option_id    BIGINT NOT NULL,
    created_at   DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_user_feedback_options PRIMARY KEY (id),
    CONSTRAINT fk_user_feedback_options_feedback_id FOREIGN KEY (feedback_id) REFERENCES user_feedbacks(id),
    CONSTRAINT uk_user_feedback_options_feedback_id_option_id UNIQUE (feedback_id, option_id)
) ENGINE = INNODB;
