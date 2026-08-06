-- 같은 사용자가 같은 채널에 동일 수신 경로를 중복 등록하지 못하게 한다.
-- 소프트 삭제된 과거 endpoint는 이력 보존을 위해 중복 검사에서 제외한다.
CREATE UNIQUE INDEX uq_active_notification_endpoint
    ON notification_endpoint (user_id, channel_type_id, destination)
    WHERE is_deleted = FALSE;
