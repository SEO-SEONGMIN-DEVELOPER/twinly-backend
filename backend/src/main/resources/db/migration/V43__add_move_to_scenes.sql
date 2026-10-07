ALTER TABLE scenes
    MODIFY COLUMN type ENUM ('ACTION','DIALOGUE','MOVE') NOT NULL,
    ADD COLUMN from_place      TEXT,
    ADD COLUMN from_place_code VARCHAR(50),
    ADD COLUMN travel_mode     VARCHAR(20),
    ADD COLUMN map_version     VARCHAR(100);
