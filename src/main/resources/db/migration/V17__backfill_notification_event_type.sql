-- 기존 notification의 event type 보정을 제약 추가와 분리한다.
-- 운영 대형 테이블에서는 이 migration 전에 같은 WHERE 조건으로 운영 배치 backfill을 반복 실행하고,
-- 남은 row가 없음을 확인한 뒤 적용한다. 현재 로컬 단계에서는 전체 보정을 한 번에 수행한다.

-- V3에서 NotificationDelivery로 이관된 template의 event type으로 보정한다.
UPDATE notification n
SET notification_event_type_id = (
    SELECT t.notification_event_type_id
    FROM notification_delivery d
    JOIN notification_template t ON t.id = d.notification_template_id
    WHERE d.notification_id = n.id
    ORDER BY d.id
    LIMIT 1
)
WHERE n.notification_event_type_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM notification_delivery d
      JOIN notification_template t ON t.id = d.notification_template_id
      WHERE d.notification_id = n.id
  );

-- Delivery가 없는 과거 notification은 명시적 LEGACY 유형으로 보존한다.
UPDATE notification n
SET notification_event_type_id = legacy_event_type.id
FROM notification_event_type legacy_event_type
WHERE n.notification_event_type_id IS NULL
  AND legacy_event_type.code = 'LEGACY_NOTIFICATION';
