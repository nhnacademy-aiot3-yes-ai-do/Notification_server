-- 2026-08-04 RabbitMQ 연동 회의에서 제어 성공도 사용자에게 1회 알림하기로 확정했다.
-- 이미 적용된 V2 Seed는 수정하지 않고 새 Migration으로 기준 데이터를 추가한다.

INSERT INTO notification_event_type (code, display_name, description, target_type)
SELECT
    'ACTUATOR_CONTROL_SUCCEEDED',
    '제어 성공',
    '장치 ON 또는 OFF 제어가 성공함',
    t.id
FROM subscription_target_type t
WHERE t.target_type = 'CULTIVATION'
ON CONFLICT (code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    target_type = EXCLUDED.target_type,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO notification_subscription_type
    (notification_event_type_id, subscription_target_type_id,
     notification_subscription_name, description)
SELECT
    e.id,
    t.id,
    '제어 성공 알림',
    '재배 장치 ON 또는 OFF 제어 성공 알림'
FROM notification_event_type e
JOIN subscription_target_type t ON t.target_type = 'CULTIVATION'
WHERE e.code = 'ACTUATOR_CONTROL_SUCCEEDED'
ON CONFLICT (notification_event_type_id, subscription_target_type_id) DO UPDATE
SET notification_subscription_name = EXCLUDED.notification_subscription_name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO subscription_channel (notification_subscription_type_id, channel_type_id)
SELECT s.id, c.id
FROM notification_subscription_type s
JOIN notification_event_type e ON e.id = s.notification_event_type_id
CROSS JOIN channel_type c
WHERE e.code = 'ACTUATOR_CONTROL_SUCCEEDED'
ON CONFLICT (notification_subscription_type_id, channel_type_id) DO NOTHING;

INSERT INTO notification_template
    (notification_event_type_id, channel_type_id, body_template, version)
SELECT
    e.id,
    c.id,
    '[제어 성공] {{cultivationName}}의 {{deviceName}} 장치가 {{controlType}} 상태로 변경되었습니다.',
    1
FROM notification_event_type e
CROSS JOIN channel_type c
WHERE e.code = 'ACTUATOR_CONTROL_SUCCEEDED'
ON CONFLICT (notification_event_type_id, channel_type_id, version) DO UPDATE
SET body_template = EXCLUDED.body_template,
    updated_at = CURRENT_TIMESTAMP;
