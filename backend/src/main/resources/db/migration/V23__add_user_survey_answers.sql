CREATE TABLE user_survey_answers (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    question_id  INTEGER NOT NULL,
    option_name  ENUM ('A','B') NOT NULL,
    created_at   DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_user_survey_answers PRIMARY KEY (id),
    CONSTRAINT fk_user_survey_answers_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_user_survey_answers_user_id_question_id UNIQUE (user_id, question_id)
) ENGINE = INNODB;
