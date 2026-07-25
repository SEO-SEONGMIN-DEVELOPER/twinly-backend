/* 이름, 성 분리 */
ALTER TABLE users
    DROP COLUMN name,
    ADD COLUMN family_name TEXT NOT NULL,
    ADD COLUMN given_name   TEXT NOT NULL,
    DROP COLUMN height,
    ADD COLUMN withdrawal_scheduled_at TIMESTAMPTZ;


/* 암호화 컬럼의 속성을 TEXT로 변환 */
ALTER TABLE users
    ALTER COLUMN birth_date TYPE TEXT,
    ALTER COLUMN birth_date SET NOT NULL;

/* chats 의 message type 컬럼 추가 */
CREATE TYPE CHAT_MESSAGE_TYPE AS ENUM (
    'TEXT',
    'PHOTO',
    'VIDEO'
);

ALTER TABLE chats
    ADD COLUMN type CHAT_MESSAGE_TYPE NOT NULL;

/* blocks: 테이블명과 컬럼의 중복 의미 제거 */
ALTER TABLE blocks RENAME COLUMN blocker_user_id TO user_id;
ALTER TABLE blocks RENAME CONSTRAINT fk_blocks_blocker_user_id TO fk_blocks_user_id;
ALTER TABLE blocks RENAME CONSTRAINT uk_blocks_blocker_user_id_blocked_user_id TO uk_blocks_user_id_blocked_user_id;

/* report status를 enum으로 변경 */
CREATE TYPE REPORT_STATUS AS ENUM (
    'PENDING',
    'IN_REVIEW',
    'REJECTED',
    'RESOLVED'
);

ALTER TABLE reports
    ALTER COLUMN status TYPE REPORT_STATUS USING status::REPORT_STATUS,
    ALTER COLUMN status SET NOT NULL;

/* reports: 테이블명과 컬럼의 중복 의미 제거 */
ALTER TABLE reports RENAME COLUMN reporter_user_id TO user_id;
ALTER TABLE reports RENAME CONSTRAINT fk_reports_reporter_user_id TO fk_reports_user_id;

/* photo가 업데이트되면 기존 사진은 삭제 */
ALTER TABLE photos
    DROP COLUMN is_current CASCADE;

/* version이 agreements 안에 있는 게 어색. 약관 테이블을 따로 만드는 게 적합 */
CREATE TABLE policy_names (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    name            TEXT NOT NULL,
    identifier      TEXT NOT NULL,
    is_deprecated   BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_policy_names PRIMARY KEY (id)
);

CREATE TABLE policies (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    policy_name_id   BIGINT NOT NULL,
    version          INTEGER NOT NULL,
    content          TEXT NOT NULL,
    url              TEXT NOT NULL,
    is_required      BOOLEAN NOT NULL DEFAULT TRUE,
    effective_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

   CONSTRAINT pk_policies PRIMARY KEY (id),
   CONSTRAINT fk_policies_policy_name_id FOREIGN KEY (policy_name_id) REFERENCES policy_names (id),
   CONSTRAINT uk_policies_policy_name_id_version UNIQUE (policy_name_id, version)
);

ALTER TABLE agreements
    DROP COLUMN type,
    DROP COLUMN version,
    ADD COLUMN policy_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_agreements_policy_id FOREIGN KEY (policy_id) REFERENCES policies (id);

/* agreements.type 컬럼 제거 후 미사용 enum 타입 삭제 */
DROP TYPE AGREEMENT_TYPE;

/* user별 디바이스 정보, 푸시 토큰 컬럼 추가 */
CREATE TABLE devices (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,
    device_id       UUID NOT NULL,
    device_model    TEXT NOT NULL,
    push_token      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT fk_devices_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_devices_device_id UNIQUE (device_id)
);

CREATE INDEX ix_devices_user_id ON devices (user_id);