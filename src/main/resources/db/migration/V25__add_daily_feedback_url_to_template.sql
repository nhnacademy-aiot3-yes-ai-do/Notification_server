-- 이미 적용된 Seed는 변경하지 않는다.
-- Processor가 만든 공개 feedbackUrl을 Discord/Telegram 템플릿에 표시한다.
UPDATE notification_template template
SET body_template = '[AI 일일 피드백] {{cultivationName}}의 오늘 피드백이 생성되었습니다.
{{feedbackSummary}}
{{feedbackUrl}}',
    updated_at = CURRENT_TIMESTAMP
FROM notification_event_type event_type
WHERE template.notification_event_type_id = event_type.id
  AND event_type.code = 'DAILY_FEEDBACK_COMPLETED';
