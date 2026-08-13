-- V17 backfill 이후 기존 notification row를 검증한다.
-- VALIDATE는 신규 쓰기를 막는 강한 ACCESS EXCLUSIVE lock 없이 기존 row를 검사한다.
ALTER TABLE notification
    VALIDATE CONSTRAINT fk_notification_event_type;

ALTER TABLE notification
    VALIDATE CONSTRAINT chk_notification_event_type_not_null;
