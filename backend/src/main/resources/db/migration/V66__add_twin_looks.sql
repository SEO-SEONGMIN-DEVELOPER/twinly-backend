CREATE TABLE twin_looks (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    hair          INT NOT NULL,
    hair_color    INT NOT NULL,
    outfit        INT NOT NULL,
    outfit_color  INT NOT NULL,
    created_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,
    updated_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_twin_looks PRIMARY KEY (id),
    CONSTRAINT fk_twin_looks_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_twin_looks_user_id UNIQUE (user_id)
) ENGINE = INNODB;
