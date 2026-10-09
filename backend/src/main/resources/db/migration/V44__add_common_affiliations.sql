CREATE TABLE common_affiliations (
    id    BIGINT NOT NULL AUTO_INCREMENT,
    name  VARCHAR(100) NOT NULL,

    CONSTRAINT pk_common_affiliations PRIMARY KEY (id),
    CONSTRAINT uk_common_affiliations_name UNIQUE (name)
) ENGINE = INNODB;
