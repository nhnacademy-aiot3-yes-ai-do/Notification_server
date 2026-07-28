-- enabled=false인 일시정지 구독은 같은 조건으로 새 구독을 만들 수 있어야 한다.
DROP INDEX IF EXISTS uq_active_notification_subscription;

CREATE UNIQUE INDEX uq_active_notification_subscription
    ON notification_subscription (
        notification_subscription_type_id,
        notification_endpoint_id,
        target_id
    )
    WHERE is_deleted = FALSE AND enabled = TRUE;
