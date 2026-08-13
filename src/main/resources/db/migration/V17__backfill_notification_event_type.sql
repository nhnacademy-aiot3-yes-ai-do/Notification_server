-- 대형 notification 테이블의 backfill은 배포 migration에서 수행하지 않는다.
-- 운영 배치 scripts/db/operations/backfill_notification_event_type.sql를 반복 실행해
-- notification_event_type_id IS NULL row가 없어진 뒤에만 이 migration을 적용한다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM notification
        WHERE notification_event_type_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'notification_event_type backfill is incomplete; run scripts/db/operations/backfill_notification_event_type.sql before V17';
    END IF;
END;
$$;
