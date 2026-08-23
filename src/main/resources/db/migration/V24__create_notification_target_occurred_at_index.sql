-- 운영 알림 쓰기를 오래 막지 않도록 트랜잭션 밖에서 인덱스를 생성한다.
CREATE INDEX CONCURRENTLY idx_notification_target_occurred_at
    ON notification (target_id, occurred_at)
    WHERE target_id IS NOT NULL AND occurred_at IS NOT NULL;
