CREATE TABLE schools (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    name    VARCHAR(100) NOT NULL,
    domain  VARCHAR(255) NOT NULL,

    CONSTRAINT pk_schools PRIMARY KEY (id),
    CONSTRAINT uk_schools_domain UNIQUE (domain)
) ENGINE = INNODB;

CREATE TABLE school_affiliations (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    school_id  BIGINT NOT NULL,
    name       VARCHAR(100) NOT NULL,

    CONSTRAINT pk_school_affiliations PRIMARY KEY (id),
    CONSTRAINT fk_school_affiliations_school_id FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT uk_school_affiliations_school_id_name UNIQUE (school_id, name)
) ENGINE = INNODB;
