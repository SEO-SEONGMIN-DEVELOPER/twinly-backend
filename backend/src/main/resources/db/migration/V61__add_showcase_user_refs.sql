ALTER TABLE showcases
    ADD COLUMN ref_version INT DEFAULT 0 NOT NULL;

CREATE TABLE showcase_user_refs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    showcase_id BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    user_ref    BIGINT      NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_showcase_user_refs PRIMARY KEY (id),
    CONSTRAINT fk_showcase_user_refs_showcase_id FOREIGN KEY (showcase_id) REFERENCES showcases(id),
    CONSTRAINT fk_showcase_user_refs_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_showcase_user_refs_showcase_id_user_id UNIQUE (showcase_id, user_id),
    CONSTRAINT uk_showcase_user_refs_showcase_id_user_ref UNIQUE (showcase_id, user_ref)
) ENGINE = INNODB;
