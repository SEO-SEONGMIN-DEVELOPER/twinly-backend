UPDATE early_signup_grants
SET expires_at = '2026-12-31 15:00:00',
    granted_at = NULL
WHERE expires_at < '2026-12-31 15:00:00';
