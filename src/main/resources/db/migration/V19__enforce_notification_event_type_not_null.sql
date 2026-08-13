-- V18에서 검증된 CHECK를 근거로 NOT NULL을 확정한다.
ALTER TABLE notification
    ALTER COLUMN notification_event_type_id SET NOT NULL;

ALTER TABLE notification
    DROP CONSTRAINT chk_notification_event_type_not_null;
