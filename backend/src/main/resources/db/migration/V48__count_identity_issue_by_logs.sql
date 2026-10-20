ALTER TABLE anon_session_identity_verifications
    MODIFY issue_window_started_at DATETIME(6) NULL,
    MODIFY issue_count INT NULL;

CREATE INDEX ix_identity_verification_logs_anon_session_id_created_at
    ON identity_verification_logs (anon_session_id, created_at);
