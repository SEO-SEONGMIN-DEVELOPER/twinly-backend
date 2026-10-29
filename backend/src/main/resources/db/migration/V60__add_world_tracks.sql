CREATE TABLE world_tracks (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    pool_number    INT         NOT NULL,
    date           DATE        NOT NULL,
    sector_code    VARCHAR(10) NOT NULL,
    viewer_user_id BIGINT      NOT NULL,
    body           LONGBLOB    NOT NULL,
    received_at    DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_world_tracks PRIMARY KEY (id),
    CONSTRAINT uk_world_tracks_pool_number_date_sector_code_viewer_user_id UNIQUE (pool_number, date, sector_code, viewer_user_id)
) ENGINE = INNODB;
