ALTER TABLE users
    ADD COLUMN family_name_hash        TEXT NOT NULL,
    ADD COLUMN given_name_hash         TEXT NOT NULL,
    ADD COLUMN affiliation_hash        TEXT NOT NULL,
    ADD COLUMN affiliation_number_hash TEXT NOT NULL,
    ADD COLUMN birth_date_hash         TEXT NOT NULL;