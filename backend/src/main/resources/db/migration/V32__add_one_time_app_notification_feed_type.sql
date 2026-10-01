ALTER TABLE app_notification_feeds
    MODIFY type ENUM ('FRIEND','MATCH','TWIN_VIEW','ONE_TIME') NOT NULL;
