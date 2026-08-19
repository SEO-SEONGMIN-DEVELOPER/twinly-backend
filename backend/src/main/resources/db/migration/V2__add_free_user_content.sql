CREATE TABLE parallel_relation_codes (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    code        CHAR(6) NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_parallel_relation_codes PRIMARY KEY (id),
    CONSTRAINT fk_parallel_relation_codes_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_parallel_relation_codes_user_id UNIQUE (user_id),
    CONSTRAINT uk_parallel_relation_codes_code UNIQUE (code)
) ENGINE = INNODB;

CREATE TABLE parallel_relations (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    user_a_id      BIGINT NOT NULL,
    user_b_id      BIGINT NOT NULL,
    code_owner_id  BIGINT NOT NULL,
    similarity     INTEGER NOT NULL,
    relation       ENUM ('ENEMY','STRANGER','AWKWARD','CLOSE','BEST_FRIEND') NOT NULL,
    story_index    INTEGER NOT NULL,
    created_at     DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_parallel_relations PRIMARY KEY (id),
    CONSTRAINT fk_parallel_relations_user_a_id FOREIGN KEY (user_a_id) REFERENCES users(id),
    CONSTRAINT fk_parallel_relations_user_b_id FOREIGN KEY (user_b_id) REFERENCES users(id),
    CONSTRAINT uk_parallel_relations_user_a_id_user_b_id UNIQUE (user_a_id, user_b_id),
    CONSTRAINT ck_parallel_relations_user_order CHECK ((user_a_id < user_b_id))
) ENGINE = INNODB;

CREATE INDEX ix_parallel_relations_user_b_id ON parallel_relations (user_b_id);
