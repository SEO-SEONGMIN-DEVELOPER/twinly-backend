CREATE TABLE seed_resource_hashes (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    resource    VARCHAR(255) NOT NULL,
    hash        CHAR(64) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_seed_resource_hashes PRIMARY KEY (id),
    CONSTRAINT uk_seed_resource_hashes_resource UNIQUE (resource)
) ENGINE = INNODB;
