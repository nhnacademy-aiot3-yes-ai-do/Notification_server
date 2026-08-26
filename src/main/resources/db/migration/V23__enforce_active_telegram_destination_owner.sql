-- Telegram private chat은 하나의 MushMush 사용자만 active endpoint로 소유할 수 있다.
-- 기존 중복 데이터는 삭제하거나 임의로 재배정하지 않고 migration을 중단해 수동 해결을 요구한다.
DO $$
DECLARE
    telegram_channel_type_id BIGINT;
BEGIN
    SELECT id
      INTO telegram_channel_type_id
      FROM channel_type
     WHERE code = 'TELEGRAM'
       AND is_deleted = FALSE;

    IF telegram_channel_type_id IS NULL THEN
        RAISE EXCEPTION 'Active TELEGRAM channel type is required before enforcing Telegram endpoint ownership';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM notification_endpoint
         WHERE channel_type_id = telegram_channel_type_id
           AND is_deleted = FALSE
         GROUP BY destination
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Resolve duplicate active Telegram endpoint ownership before this migration';
    END IF;

    EXECUTE format(
            'CREATE UNIQUE INDEX uq_active_telegram_notification_endpoint_destination
               ON notification_endpoint (destination)
             WHERE is_deleted = FALSE AND channel_type_id = %s',
            telegram_channel_type_id);
END $$;
