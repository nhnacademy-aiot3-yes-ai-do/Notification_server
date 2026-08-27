-- Cultivation MEMBER_ADDED(재배지 멤버 추가) 계약.
-- 기존 CULTIVATION_MEMBER_INVITED seed는 수정하지 않고 새 유형만 추가한다.

INSERT INTO notification_event_type (code, display_name, description, target_type)
SELECT
    'MEMBER_ADDED',
    '재배 멤버 추가',
    '재배지에 멤버가 추가됨',
    t.id
FROM subscription_target_type t
WHERE t.target_type = 'USER'
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
    '멤버 추가 알림',
    '재배지에 멤버가 추가됨 알림'
FROM notification_event_type e
JOIN subscription_target_type t ON t.target_type = 'USER'
WHERE e.code = 'MEMBER_ADDED'
ON CONFLICT (notification_event_type_id, subscription_target_type_id) DO UPDATE
SET notification_subscription_name = EXCLUDED.notification_subscription_name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 채널 코드는 V2 seed의 channel_type.code(TELEGRAM, DISCORD)와 일치한다.
-- 이후 채널이 추가돼도 MEMBER_ADDED 템플릿·구독 채널은 이 두 개만 만든다.
INSERT INTO subscription_channel (notification_subscription_type_id, channel_type_id)
SELECT s.id, c.id
FROM notification_subscription_type s
JOIN notification_event_type e ON e.id = s.notification_event_type_id
JOIN channel_type c ON c.code IN ('DISCORD', 'TELEGRAM')
WHERE e.code = 'MEMBER_ADDED'
ON CONFLICT (notification_subscription_type_id, channel_type_id) DO NOTHING;

INSERT INTO notification_template
    (notification_event_type_id, channel_type_id, body_template, version)
SELECT
    e.id,
    c.id,
    '[재배지 멤버 추가] {{cultivationName}}에 멤버로 추가되었습니다. 역할: {{role}}',
    1
FROM notification_event_type e
JOIN channel_type c ON c.code IN ('DISCORD', 'TELEGRAM')
WHERE e.code = 'MEMBER_ADDED'
ON CONFLICT (notification_event_type_id, channel_type_id, version) DO UPDATE
SET body_template = EXCLUDED.body_template,
    updated_at = CURRENT_TIMESTAMP;

-- V2 seed는 {{harvestAmount}}이고, Cultivation/Processor는 harvestWeight를 쓴다.
-- V11이 이미 템플릿을 harvestWeight로 맞췄다. V2는 수정하지 않고 V24에서 한 번 더 맞춰
-- 신규 DB와 기존 DB 모두 수확량 변수가 비지 않게 한다.
UPDATE notification_template template
SET body_template = '[수확 완료] {{cultivationName}}의 수확이 완료되었습니다. 수확량: {{harvestWeight}}g',
    updated_at = CURRENT_TIMESTAMP
FROM notification_event_type event_type
WHERE template.notification_event_type_id = event_type.id
  AND event_type.code = 'HARVEST_COMPLETED';
