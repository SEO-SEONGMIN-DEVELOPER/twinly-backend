ALTER TABLE organizations
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'SCHOOL';

ALTER TABLE organizations
    ALTER COLUMN type DROP DEFAULT;

ALTER TABLE organizations
    ADD COLUMN school_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN type = 'SCHOOL' THEN name END) STORED,
    ADD CONSTRAINT uk_organizations_school_name UNIQUE (school_name),
    DROP INDEX uk_organizations_name;

ALTER TABLE users
    ADD COLUMN organization_type VARCHAR(20) NOT NULL DEFAULT 'SCHOOL';

ALTER TABLE users
    ALTER COLUMN organization_type DROP DEFAULT;

ALTER TABLE pool_counter
    ADD COLUMN organization_type VARCHAR(20) NULL,
    ADD COLUMN first_pool_number INT NULL;

UPDATE pool_counter
SET organization_type = 'SCHOOL',
    first_pool_number = 13
WHERE id = 1;

INSERT INTO pool_counter (id, assigned_count, organization_type, first_pool_number)
VALUES (2, 0, 'COMPANY', 100001);

ALTER TABLE pool_counter
    MODIFY COLUMN organization_type VARCHAR(20) NOT NULL,
    MODIFY COLUMN first_pool_number INT NOT NULL,
    ADD CONSTRAINT uk_pool_counter_organization_type UNIQUE (organization_type);
