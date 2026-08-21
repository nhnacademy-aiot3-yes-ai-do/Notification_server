-- Cultivation Service의 확정 payload 필드명(harvestWeight)에 맞춘다.
-- 이미 적용된 V2 Seed는 변경하지 않고, 후속 Migration으로 기존 템플릿을 보정한다.
UPDATE notification_template template
SET body_template = '[수확 완료] {{cultivationName}}의 수확이 완료되었습니다. 수확량: {{harvestWeight}}g'
FROM notification_event_type event_type
WHERE template.notification_event_type_id = event_type.id
  AND event_type.code = 'HARVEST_COMPLETED';
