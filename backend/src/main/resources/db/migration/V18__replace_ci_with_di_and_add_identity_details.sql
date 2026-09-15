ALTER TABLE anon_session_identity_verifications
    RENAME COLUMN ci TO di,
    RENAME COLUMN ci_hash TO di_hash,
    ADD COLUMN national_info  ENUM ('DOMESTIC','FOREIGN') AFTER gender,
    ADD COLUMN mobile_carrier ENUM ('SKT','KT','LGU','SKT_MVNO','KT_MVNO','LGU_MVNO') AFTER phone_number;

UPDATE anon_session_identity_verifications SET di = NULL, di_hash = NULL;

ALTER TABLE users
    RENAME COLUMN ci TO di,
    RENAME COLUMN ci_hash TO di_hash,
    RENAME INDEX uk_users_ci_hash TO uk_users_di_hash,
    ADD COLUMN national_info  ENUM ('DOMESTIC','FOREIGN') AFTER gender,
    ADD COLUMN mobile_carrier ENUM ('SKT','KT','LGU','SKT_MVNO','KT_MVNO','LGU_MVNO') AFTER phone_number_hash;

UPDATE users SET di = NULL, di_hash = NULL;
