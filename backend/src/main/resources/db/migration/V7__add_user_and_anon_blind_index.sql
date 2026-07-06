ALTER TABLE users
    ADD COLUMN phone_number_hash TEXT NOT NULL,
    ADD COLUMN email_hash        TEXT NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT uk_users_phone_number,
    DROP CONSTRAINT uk_users_email;

ALTER TABLE users
    ADD CONSTRAINT uk_users_phone_number_hash UNIQUE (phone_number_hash),
    ADD CONSTRAINT uk_users_email_hash UNIQUE (email_hash);

ALTER TABLE anon_sessions
    ADD COLUMN phone_number_hash TEXT,
    ADD COLUMN email_hash        TEXT;

ALTER TABLE anon_sessions
    ADD CONSTRAINT uk_anon_sessions_phone_number_hash UNIQUE (phone_number_hash),
    ADD CONSTRAINT uk_anon_sessions_email_hash UNIQUE (email_hash);