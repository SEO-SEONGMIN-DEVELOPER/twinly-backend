ALTER TABLE anon_session_identity_verifications
    RENAME COLUMN identity_verification_id TO request_no,
    RENAME INDEX uk_anon_session_identity_verifications_verification_id TO uk_anon_session_identity_verifications_request_no,
    ADD COLUMN transaction_id VARCHAR(255) AFTER request_no;
