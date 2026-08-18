INSERT INTO organizations (name)
VALUES ('성균관대학교'),
       ('성신여자대학교'),
       ('고려대학교') AS new
ON DUPLICATE KEY UPDATE name = new.name;

INSERT INTO organization_domains (organization_id, domain)
VALUES ((SELECT id FROM organizations WHERE name = '성균관대학교'), 'skku.edu'),
       ((SELECT id FROM organizations WHERE name = '성균관대학교'), 'g.skku.edu'),
       ((SELECT id FROM organizations WHERE name = '성신여자대학교'), 'sungshin.ac.kr'),
       ((SELECT id FROM organizations WHERE name = '고려대학교'), 'korea.ac.kr') AS new
ON DUPLICATE KEY UPDATE organization_id = new.organization_id;
