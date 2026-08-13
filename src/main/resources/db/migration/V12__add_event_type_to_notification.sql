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

-- Delivery가 없던 과거 Notification도 보존한다. LEGACY는 새 RabbitMQ 이벤트 정의가 아니라
-- 과거 이력 전용 전략이며 구독/발송 대상에는 사용하지 않는다.
INSERT INTO subscription_target_type (target_type, display_name)
VALUES ('LEGACY', '과거 알림 이력')
ON CONFLICT (target_type) DO NOTHING;

INSERT INTO notification_event_type (code, display_name, description, target_type)
SELECT 'LEGACY_NOTIFICATION', '과거 알림 이력', 'delivery가 없는 이전 notification 이력', t.id
FROM subscription_target_type t
WHERE t.target_type = 'LEGACY'
ON CONFLICT (code) DO NOTHING;

-- NOT NULL 제약을 적용하기 전에 delivery로 유추할 수 없는 모든 기존 row를 명시적 LEGACY로 채운다.
UPDATE notification n
SET notification_event_type_id = legacy_event_type.id
FROM notification_event_type legacy_event_type
WHERE n.notification_event_type_id IS NULL
  AND legacy_event_type.code = 'LEGACY_NOTIFICATION';

ALTER TABLE notification
    ALTER COLUMN notification_event_type_id SET NOT NULL;

ALTER TABLE notification
    ADD CONSTRAINT fk_notification_event_type
    FOREIGN KEY (notification_event_type_id)
    REFERENCES notification_event_type (id);

CREATE INDEX idx_notification_event_type_created
    ON notification (notification_event_type_id, created_at DESC);
