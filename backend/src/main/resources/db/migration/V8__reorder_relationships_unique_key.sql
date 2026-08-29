ALTER TABLE relationships
    DROP INDEX uk_relationships_user_id_partner_user_id_date,
    ADD CONSTRAINT uk_relationships_user_id_partner_user_id_date UNIQUE (user_id, partner_user_id, date);
