ALTER TABLE scenes
    ADD COLUMN activity VARCHAR(100),
    ADD COLUMN motion   VARCHAR(100);

CREATE TABLE simulation_home_objects (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    date        DATE NOT NULL,
    objects     JSON NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_simulation_home_objects PRIMARY KEY (id),
    CONSTRAINT fk_simulation_home_objects_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_simulation_home_objects_user_id_date UNIQUE (user_id, date)
) ENGINE = INNODB;

CREATE TABLE user_rooms (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    room        JSON NOT NULL,
    date        DATE NOT NULL,
    created_at  DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_user_rooms PRIMARY KEY (id),
    CONSTRAINT fk_user_rooms_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_user_rooms_user_id UNIQUE (user_id)
) ENGINE = INNODB;
