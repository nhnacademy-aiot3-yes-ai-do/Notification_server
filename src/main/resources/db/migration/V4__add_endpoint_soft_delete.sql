ALTER TABLE notification_endpoint
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_endpoint_user_active
    ON notification_endpoint (user_id, enabled, is_deleted);
