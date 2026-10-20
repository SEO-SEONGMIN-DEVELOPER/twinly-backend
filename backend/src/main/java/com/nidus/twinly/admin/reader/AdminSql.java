package com.nidus.twinly.admin.reader;

final class AdminSql {

    static final String ACTIVE_USER = "u.is_seed = FALSE AND u.deleted_at IS NULL AND u.withdrawal_requested_at IS NULL";

    static final String ACTIVE_SIMULATION_ACCESS = "e.entitlement = 'simulation_access' AND (e.expires_at IS NULL OR e.expires_at > UTC_TIMESTAMP(6))";

    static final String CURRENT_SEASON_PARTICIPANT = """
            EXISTS (SELECT 1 FROM season_participations sp
                    WHERE sp.user_id = u.id
                      AND sp.season_id = (SELECT s.id FROM seasons s WHERE s.is_active = TRUE ORDER BY s.id DESC LIMIT 1))""";

    static final String SUBSCRIPTION_KIND = """
            CASE
                WHEN LOWER(e.store) = 'promotional'
                     OR (e.store IS NULL AND EXISTS (SELECT 1 FROM early_signup_grants g
                                                     WHERE g.user_id = e.user_id AND g.granted_at IS NOT NULL))
                    THEN 'EARLY_GRANT'
                WHEN LOWER(e.period_type) = 'trial' THEN 'TRIAL'
                ELSE 'PAID'
            END""";

    private AdminSql() {
    }
}
