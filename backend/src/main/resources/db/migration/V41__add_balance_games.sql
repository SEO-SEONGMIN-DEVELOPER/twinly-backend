CREATE TABLE balance_game_rounds (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    starts_at        DATETIME(6) NOT NULL,
    question_id      BIGINT NOT NULL,
    summary_sent_at  DATETIME(6),
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_balance_game_rounds PRIMARY KEY (id),
    CONSTRAINT uk_balance_game_rounds_starts_at UNIQUE (starts_at)
) ENGINE = INNODB;

CREATE TABLE balance_game_answers (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    round_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    option_id   BIGINT NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_balance_game_answers PRIMARY KEY (id),
    CONSTRAINT fk_balance_game_answers_round_id FOREIGN KEY (round_id) REFERENCES balance_game_rounds(id),
    CONSTRAINT fk_balance_game_answers_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_balance_game_answers_round_id_user_id UNIQUE (round_id, user_id)
) ENGINE = INNODB;

CREATE INDEX ix_balance_game_answers_round_id_option_id ON balance_game_answers (round_id, option_id);
