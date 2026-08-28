ALTER TABLE users
    ADD COLUMN revenue_cat_user_id BINARY(16);

UPDATE users
SET revenue_cat_user_id = RANDOM_BYTES(16)
WHERE revenue_cat_user_id IS NULL;

ALTER TABLE users
    MODIFY revenue_cat_user_id BINARY(16) NOT NULL,
    ADD CONSTRAINT uk_users_revenue_cat_user_id UNIQUE (revenue_cat_user_id);
