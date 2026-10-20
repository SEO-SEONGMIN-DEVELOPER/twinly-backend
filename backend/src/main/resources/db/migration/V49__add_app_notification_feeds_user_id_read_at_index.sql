CREATE INDEX ix_app_notification_feeds_user_id_read_at ON app_notification_feeds (user_id, read_at);

DROP INDEX ix_notification_feeds_notification_id ON app_notification_feeds;
