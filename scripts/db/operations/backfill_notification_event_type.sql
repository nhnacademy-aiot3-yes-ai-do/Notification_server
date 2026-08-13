-- PostgreSQL 운영 배치: notification event type backfill
--
-- 사용 방법(autocommit가 켜진 psql 세션에서 실행):
--   CALL backfill_notification_event_type(1000);
-- 위 CALL을 "updated rows = 0"이 될 때까지 반복한 뒤,
--   SELECT count(*) FROM notification WHERE notification_event_type_id IS NULL;
-- 결과가 0인지 확인하고 V17 migration을 적용한다.
--
-- 이 파일은 Flyway migration이 아니다. 대형 테이블에서 한 번의 전체 UPDATE와 긴 배포 transaction을 피하기 위한
-- 재개 가능한 운영 절차다. 각 CALL은 최대 batch_size row를 갱신하고 독립적으로 commit한다.

CREATE OR REPLACE PROCEDURE backfill_notification_event_type(batch_size INTEGER)
LANGUAGE plpgsql
AS $$
DECLARE
    updated_rows INTEGER;
BEGIN
    IF batch_size IS NULL OR batch_size < 1 THEN
        RAISE EXCEPTION 'batch_size must be greater than zero';
    END IF;

    WITH batch AS (
        SELECT n.id
        FROM notification n
        WHERE n.notification_event_type_id IS NULL
        ORDER BY n.id
        FOR UPDATE SKIP LOCKED
        LIMIT batch_size
    ), event_type_by_delivery AS (
        SELECT DISTINCT ON (d.notification_id)
            d.notification_id,
            template.notification_event_type_id
        FROM notification_delivery d
        JOIN notification_template template ON template.id = d.notification_template_id
        JOIN batch ON batch.id = d.notification_id
        ORDER BY d.notification_id, d.id
    ), legacy_event_type AS (
        SELECT id
        FROM notification_event_type
        WHERE code = 'LEGACY_NOTIFICATION'
    )
    UPDATE notification n
    SET notification_event_type_id = COALESCE(event_type_by_delivery.notification_event_type_id, legacy_event_type.id)
    FROM batch
    CROSS JOIN legacy_event_type
    LEFT JOIN event_type_by_delivery ON event_type_by_delivery.notification_id = batch.id
    WHERE n.id = batch.id;

    GET DIAGNOSTICS updated_rows = ROW_COUNT;
    RAISE NOTICE 'backfilled notification event types: % row(s)', updated_rows;
END;
$$;
