-- enabled=false는 삭제가 아니라 일시정지다.
-- 비삭제 구독은 활성 여부와 관계없이 하나만 유지하고, 재구독 시 기존 행을 다시 활성화한다.
DROP INDEX IF EXISTS uq_active_notification_subscription;

CREATE UNIQUE INDEX uq_non_deleted_notification_subscription
    ON notification_subscription (
        notification_subscription_type_id,
        notification_endpoint_id,
        target_id
    )
    WHERE is_deleted = FALSE;
