/* nickname 중복 방지 검사를 위해 index 설정 */
CREATE INDEX users_nickname ON users (nickname);

/* 이름, 성 분리 */
ALTER TABLE users
    DROP COLUMN name,
    ADD COLUMN family_name TEXT NOT NULL,
    ADD COLUMN given_name   TEXT NOT NULL;

/* 암호화 컬럼의 속성을 TEXT로 변환 */
ALTER TABLE users
    ALTER COLUMN birth_date TYPE TEXT,
    ALTER COLUMN birth_date SET NOT NULL,
    ALTER COLUMN height TYPE TEXT,
    ALTER COLUMN height SET NOT NULL;

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

CREATE INDEX photos_user_id_type ON photos (user_id, type);

/* version이 agreements 안에 있는 게 어색. 약관 테이블을 따로 만드는 게 적합 */
ALTER TYPE AGREEMENT_TYPE RENAME TO POLICY_TYPE;

CREATE TABLE policies (
   id           BIGINT GENERATED ALWAYS AS IDENTITY,
   type         POLICY_TYPE NOT NULL,
   version      TEXT NOT NULL,
   content      TEXT NOT NULL,
   effective_at TIMESTAMPTZ,
   created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

   CONSTRAINT pk_policies PRIMARY KEY (id),
   CONSTRAINT uk_policies_type_version UNIQUE (type, version)
);

ALTER TABLE agreements
    DROP COLUMN type,
    DROP COLUMN version,
    ADD COLUMN policy_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_agreements_policy_id FOREIGN KEY (policy_id) REFERENCES policies (id);

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
    CONSTRAINT uk_devices_user_id_device_id UNIQUE (user_id, device_id)
);

CREATE INDEX ix_devices_user_id ON devices (user_id);