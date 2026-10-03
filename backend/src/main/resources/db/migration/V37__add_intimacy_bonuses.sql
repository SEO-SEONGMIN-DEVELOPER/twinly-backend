CREATE TABLE intimacy_bonuses (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_a_id   BIGINT NOT NULL,
    user_b_id   BIGINT NOT NULL,
    amount      INTEGER NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_intimacy_bonuses PRIMARY KEY (id),
    CONSTRAINT fk_intimacy_bonuses_user_a_id FOREIGN KEY (user_a_id) REFERENCES users(id),
    CONSTRAINT fk_intimacy_bonuses_user_b_id FOREIGN KEY (user_b_id) REFERENCES users(id),
    CONSTRAINT ck_intimacy_bonuses_user_order CHECK ((user_a_id < user_b_id)),
    CONSTRAINT ck_intimacy_bonuses_amount CHECK ((amount > 0))
) ENGINE = INNODB;

CREATE INDEX ix_intimacy_bonuses_user_a_id_user_b_id_created_at ON intimacy_bonuses (user_a_id, user_b_id, created_at);
