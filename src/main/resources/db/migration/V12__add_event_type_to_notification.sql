-- 운영 notification 테이블을 즉시 전수 스캔하지 않도록, 새 컬럼과 신규 쓰기 보호부터 추가한다.
-- 기존 row의 backfill과 제약 검증은 후속 migration으로 분리한다.
ALTER TABLE notification
    ADD COLUMN notification_event_type_id BIGINT;

-- Delivery가 없던 과거 Notification도 후속 backfill에서 보존할 수 있도록 LEGACY 유형을 먼저 준비한다.
-- LEGACY는 새 RabbitMQ 이벤트 정의가 아니며 구독/발송 대상으로 사용하지 않는다.
INSERT INTO subscription_target_type (target_type, display_name)
VALUES ('LEGACY', '과거 알림 이력')
ON CONFLICT (target_type) DO NOTHING;

INSERT INTO notification_event_type (code, display_name, description, target_type)
SELECT 'LEGACY_NOTIFICATION', '과거 알림 이력', 'delivery가 없는 이전 notification 이력', t.id
FROM subscription_target_type t
WHERE t.target_type = 'LEGACY'
ON CONFLICT (code) DO NOTHING;

-- NOT VALID는 신규 INSERT/UPDATE에는 즉시 적용하고, 기존 row 검증은 V18로 미룬다.
ALTER TABLE notification
    ADD CONSTRAINT fk_notification_event_type
    FOREIGN KEY (notification_event_type_id)
    REFERENCES notification_event_type (id)
    NOT VALID;

-- NOT NULL은 NOT VALID를 지원하지 않으므로 동일 조건의 CHECK를 먼저 추가한다.
-- V17 backfill, V18 VALIDATE 후 V19에서 짧게 NOT NULL로 승격한다.
ALTER TABLE notification
    ADD CONSTRAINT chk_notification_event_type_not_null
    CHECK (notification_event_type_id IS NOT NULL)
    NOT VALID;
