-- Flyway must execute this migration outside a transaction.
-- PostgreSQL CREATE INDEX CONCURRENTLY avoids blocking normal notification writes.
CREATE INDEX CONCURRENTLY idx_notification_event_type_created
    ON notification (notification_event_type_id, created_at DESC);
