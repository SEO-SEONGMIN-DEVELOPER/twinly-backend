INSERT INTO policy_names (name, identifier, kind, requires_agreement)
VALUES ('서비스 이용약관', 'serviceTerms', 'ONBOARDING', TRUE),
       ('개인정보 처리방침', 'privacyPolicy', 'ONBOARDING', FALSE),
       ('개인정보 수집·이용 동의', 'privacyCollectionConsent', 'ONBOARDING', TRUE),
       ('청소년 보호 정책', 'ageRestrictionPolicy', 'ONBOARDING', FALSE),
       ('마케팅 정보 수신 동의', 'marketingConsent', 'ONBOARDING', TRUE),
       ('실신원 제3자 제공 동의', 'thirdPartyRealIdentityDisclosure', 'PARALLEL_ENTRY', TRUE) AS new
ON DUPLICATE KEY UPDATE name               = new.name,
                        kind               = new.kind,
                        requires_agreement = new.requires_agreement;
