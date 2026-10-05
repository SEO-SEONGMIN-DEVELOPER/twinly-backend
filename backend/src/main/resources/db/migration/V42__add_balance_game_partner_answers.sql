CREATE TABLE balance_game_partner_answers (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    round_id         BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,
    partner_user_id  BIGINT NOT NULL,
    option_id        BIGINT NOT NULL,
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_balance_game_partner_answers PRIMARY KEY (id),
    CONSTRAINT fk_balance_game_partner_answers_round_id FOREIGN KEY (round_id) REFERENCES balance_game_rounds(id),
    CONSTRAINT fk_balance_game_partner_answers_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_balance_game_partner_answers_partner_user_id FOREIGN KEY (partner_user_id) REFERENCES users(id),
    CONSTRAINT uk_balance_game_partner_answers_round_id_user_id_partner_user_id UNIQUE (round_id, user_id, partner_user_id),
    CONSTRAINT ck_balance_game_partner_answers_not_self CHECK ((user_id <> partner_user_id))
) ENGINE = INNODB;

CREATE INDEX ix_balance_game_partner_answers_user_id_partner_user_id ON balance_game_partner_answers (user_id, partner_user_id);
