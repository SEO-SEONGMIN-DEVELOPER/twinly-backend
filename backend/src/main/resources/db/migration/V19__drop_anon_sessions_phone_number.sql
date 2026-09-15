ALTER TABLE anon_sessions
    DROP INDEX uk_anon_sessions_phone_number_hash,
    DROP COLUMN phone_number,
    DROP COLUMN phone_number_hash;
