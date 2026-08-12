-- Notification은 생성 원인이 된 이벤트 유형을 직접 보관한다.
-- Template은 발송 단위(NotificationDelivery)에만 연결한다.
ALTER TABLE notification
    ADD COLUMN notification_event_type_id BIGINT;

-- V3에서 NotificationDelivery로 이관된 template의 이벤트 유형으로 기존 row를 보정한다.
UPDATE notification n
SET notification_event_type_id = (
    SELECT t.notification_event_type_id
    FROM notification_delivery d
    JOIN notification_template t ON t.id = d.notification_template_id
    WHERE d.notification_id = n.id
    ORDER BY d.id
    LIMIT 1
);

ALTER TABLE notification
    ALTER COLUMN notification_event_type_id SET NOT NULL;

ALTER TABLE notification
    ADD CONSTRAINT fk_notification_event_type
    FOREIGN KEY (notification_event_type_id)
    REFERENCES notification_event_type (id);

CREATE INDEX idx_notification_event_type_created
    ON notification (notification_event_type_id, created_at DESC);