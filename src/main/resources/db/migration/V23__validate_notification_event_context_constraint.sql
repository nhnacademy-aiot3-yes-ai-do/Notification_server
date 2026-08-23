-- V22에서 신규 쓰기부터 적용한 이벤트 문맥 제약을 기존 행까지 검증한다.
ALTER TABLE notification
    VALIDATE CONSTRAINT chk_notification_event_context_pair;
