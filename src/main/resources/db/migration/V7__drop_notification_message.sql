-- Notification은 이벤트 원본과 중복 방지 정보만 보관한다.
-- 실제 수신 문구는 채널별 notification_delivery.rendered_message에만 남긴다.
ALTER TABLE notification
    DROP COLUMN IF EXISTS message;
