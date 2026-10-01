CREATE TABLE app_notification_schedules (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    partner_user_id  BIGINT NOT NULL,
    type             ENUM ('FRIEND','FIRST_MEETING') NOT NULL,
    simulation_date  DATE NOT NULL,
    scheduled_at     DATETIME(6) NOT NULL,
    sent_at          DATETIME(6),
    created_at       DATETIME(6) DEFAULT (UTC_TIMESTAMP(6)) NOT NULL,

    CONSTRAINT pk_app_notification_schedules PRIMARY KEY (id),
    CONSTRAINT fk_app_notification_schedules_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_app_notification_schedules_partner_user_id FOREIGN KEY (partner_user_id) REFERENCES users(id),
    CONSTRAINT uk_app_notification_schedules_user_partner_type_date UNIQUE (user_id, partner_user_id, type, simulation_date)
) ENGINE = INNODB;

CREATE INDEX ix_app_notification_schedules_sent_at_scheduled_at ON app_notification_schedules (sent_at, scheduled_at);

INSERT INTO app_notification_schedules (user_id, partner_user_id, type, simulation_date, scheduled_at, sent_at)
SELECT first_meeting.user_id,
       first_meeting.partner_user_id,
       'FIRST_MEETING',
       first_meeting.first_date,
       MIN(s.starts_at) - INTERVAL 9 HOUR,
       IF(MIN(s.starts_at) - INTERVAL 9 HOUR <= UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL)
FROM (
    SELECT s.user_id, sp.user_id AS partner_user_id, MIN(s.date) AS first_date
    FROM scenes s
    JOIN scene_partners sp ON sp.scene_id = s.id
    WHERE s.type = 'DIALOGUE'
      AND sp.user_id <> s.user_id
    GROUP BY s.user_id, sp.user_id
) first_meeting
JOIN scenes s
    ON s.user_id = first_meeting.user_id
   AND s.date = first_meeting.first_date
   AND s.type = 'DIALOGUE'
JOIN scene_partners sp
    ON sp.scene_id = s.id
   AND sp.user_id = first_meeting.partner_user_id
GROUP BY first_meeting.user_id, first_meeting.partner_user_id, first_meeting.first_date;

INSERT INTO app_notification_schedules (user_id, partner_user_id, type, simulation_date, scheduled_at, sent_at)
SELECT r.user_id,
       r.partner_user_id,
       'FRIEND',
       r.date,
       r.update_time - INTERVAL 9 HOUR,
       UTC_TIMESTAMP(6)
FROM relationships r
WHERE r.intimacy >= 35
  AND COALESCE((
          SELECT p.intimacy
          FROM relationships p
          WHERE p.user_id = r.user_id
            AND p.partner_user_id = r.partner_user_id
            AND p.date < r.date
          ORDER BY p.date DESC
          LIMIT 1
      ), 0) < 35;
