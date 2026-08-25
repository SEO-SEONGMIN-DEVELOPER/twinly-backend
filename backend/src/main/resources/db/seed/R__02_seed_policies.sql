INSERT INTO policies (policy_name_id, version, `key`, is_required, effective_at)
VALUES ((SELECT id FROM policy_names WHERE identifier = 'serviceTerms'),
        '1.1', 'legal/service-terms/v1.1.html', TRUE, '2026-08-02 15:00:00'),
       ((SELECT id FROM policy_names WHERE identifier = 'privacyPolicy'),
        '1.1', 'legal/privacy-policy/v1.1.html', FALSE, '2026-08-02 15:00:00'),
       ((SELECT id FROM policy_names WHERE identifier = 'privacyCollectionConsent'),
        '1.1', 'legal/privacy-collection-consent/v1.1.html', TRUE, '2026-08-02 15:00:00'),
       ((SELECT id FROM policy_names WHERE identifier = 'ageRestrictionPolicy'),
        '1.1', 'legal/age-restriction-policy/v1.1.html', FALSE, '2026-08-02 15:00:00'),
       ((SELECT id FROM policy_names WHERE identifier = 'marketingConsent'),
        '1.1', 'legal/marketing-consent/v1.1.html', FALSE, '2026-08-02 15:00:00'),
       ((SELECT id FROM policy_names WHERE identifier = 'thirdPartyRealIdentityDisclosure'),
        '1.0', 'legal/third-party-real-identity-disclosure/v1.0.html', TRUE, '2026-08-02 15:00:00') AS new
ON DUPLICATE KEY UPDATE `key`        = new.`key`,
                        is_required  = new.is_required,
                        effective_at = new.effective_at;
