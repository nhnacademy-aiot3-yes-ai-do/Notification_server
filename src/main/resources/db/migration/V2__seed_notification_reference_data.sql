-- 기준 코드 Seed. 모든 INSERT는 재실행해도 중복되지 않도록 구성한다.

INSERT INTO channel_type (code, display_name)
VALUES
    ('TELEGRAM', 'Telegram'),
    ('DISCORD', 'Discord')
ON CONFLICT (code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    is_deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO subscription_target_type (target_type, display_name)
VALUES
    ('CULTIVATION', '재배'),
    ('INQUIRY', '문의'),
    ('USER', '사용자')
ON CONFLICT (target_type) DO UPDATE
SET display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO notification_event_type (code, display_name, description, target_type)
SELECT v.code, v.display_name, v.description, t.id
FROM (VALUES
    ('ENVIRONMENT_THRESHOLD_BREACHED', '환경 이상', '재배 환경값이 정상 범위를 벗어남', 'CULTIVATION'),
    ('ENVIRONMENT_RECOVERED', '환경 복구', '재배 환경값이 정상 범위로 복귀함', 'CULTIVATION'),
    ('SENSOR_OFFLINE', '센서 오프라인', '센서 연결이 끊김', 'CULTIVATION'),
    ('SENSOR_ERROR', '센서 오류', '센서 측정 또는 처리 오류', 'CULTIVATION'),
    ('ACTUATOR_CONTROL_FAILED', '제어 실패', '장치 제어에 실패함', 'CULTIVATION'),
    ('HARVEST_COMPLETED', '수확 완료', '수확 기록이 완료됨', 'CULTIVATION'),
    ('CULTIVATION_FINISHED', '재배 종료', '재배가 종료됨', 'CULTIVATION'),
    ('DAILY_FEEDBACK_COMPLETED', 'AI 일일 피드백 완료', 'AI 일일 피드백이 생성됨', 'CULTIVATION'),
    ('INQUIRY_ANSWERED', '문의 답변 완료', '사용자 문의에 답변이 등록됨', 'INQUIRY'),
    ('LOGIN_SUCCEEDED', '로그인 성공', '사용자 로그인이 완료됨', 'USER')
) AS v(code, display_name, description, target_code)
JOIN subscription_target_type t ON t.target_type = v.target_code
ON CONFLICT (code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    target_type = EXCLUDED.target_type,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO notification_subscription_type
    (notification_event_type_id, subscription_target_type_id,
     notification_subscription_name, description)
SELECT e.id, t.id, v.subscription_name, v.description
FROM (VALUES
    ('ENVIRONMENT_THRESHOLD_BREACHED', 'CULTIVATION', '환경 이상 알림', '재배 환경 이상 알림'),
    ('ENVIRONMENT_RECOVERED', 'CULTIVATION', '환경 복구 알림', '재배 환경 복구 알림'),
    ('SENSOR_OFFLINE', 'CULTIVATION', '센서 오프라인', '재배 센서 오프라인 알림'),
    ('SENSOR_ERROR', 'CULTIVATION', '센서 오류 알림', '재배 센서 오류 알림'),
    ('ACTUATOR_CONTROL_FAILED', 'CULTIVATION', '제어 실패 알림', '재배 장치 제어 실패 알림'),
    ('HARVEST_COMPLETED', 'CULTIVATION', '수확 완료 알림', '재배 수확 완료 알림'),
    ('CULTIVATION_FINISHED', 'CULTIVATION', '재배 종료 알림', '재배 종료 알림'),
    ('DAILY_FEEDBACK_COMPLETED', 'CULTIVATION', '일일 피드백 알림', 'AI 일일 피드백 완료 알림'),
    ('INQUIRY_ANSWERED', 'INQUIRY', '문의 답변 알림', '문의 답변 완료 알림'),
    ('LOGIN_SUCCEEDED', 'USER', '로그인 성공 알림', '로그인 성공 알림')
) AS v(event_code, target_code, subscription_name, description)
JOIN notification_event_type e ON e.code = v.event_code
JOIN subscription_target_type t ON t.target_type = v.target_code
ON CONFLICT (notification_event_type_id, subscription_target_type_id) DO UPDATE
SET notification_subscription_name = EXCLUDED.notification_subscription_name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO subscription_channel (notification_subscription_type_id, channel_type_id)
SELECT s.id, c.id
FROM notification_subscription_type s
CROSS JOIN channel_type c
ON CONFLICT (notification_subscription_type_id, channel_type_id) DO NOTHING;

INSERT INTO notification_template
    (notification_event_type_id, channel_type_id, body_template, version)
SELECT e.id, c.id, v.body_template, 1
FROM (VALUES
    ('ENVIRONMENT_THRESHOLD_BREACHED', '[환경 이상] {{cultivationName}}의 {{sensorType}} 값이 정상 범위를 벗어났습니다. 현재값: {{currentValue}}{{unit}} / 정상 범위: {{thresholdMin}}~{{thresholdMax}}{{unit}}'),
    ('ENVIRONMENT_RECOVERED', '[환경 복구] {{cultivationName}}의 {{sensorType}} 값이 정상 범위로 돌아왔습니다. 현재값: {{currentValue}}{{unit}}'),
    ('SENSOR_OFFLINE', '[센서 오프라인] {{cultivationName}}의 {{deviceName}} 센서가 오프라인 상태입니다.'),
    ('SENSOR_ERROR', '[센서 오류] {{cultivationName}}의 {{deviceName}} 센서 오류가 발생했습니다. 오류: {{errorMessage}}'),
    ('ACTUATOR_CONTROL_FAILED', '[제어 실패] {{cultivationName}}의 {{deviceName}} 제어에 실패했습니다. 제어 종류: {{controlType}}'),
    ('HARVEST_COMPLETED', '[수확 완료] {{cultivationName}}의 수확이 완료되었습니다. 수확량: {{harvestAmount}}g'),
    ('CULTIVATION_FINISHED', '[재배 종료] {{cultivationName}} 재배가 종료되었습니다.'),
    ('DAILY_FEEDBACK_COMPLETED', '[AI 일일 피드백] {{cultivationName}}의 오늘 피드백이 생성되었습니다. {{feedbackSummary}}'),
    ('INQUIRY_ANSWERED', '[문의 답변] 문의 {{inquiryTitle}}에 답변이 등록되었습니다.'),
    ('LOGIN_SUCCEEDED', '[로그인 알림] 계정 로그인이 완료되었습니다. 로그인 방식: {{provider}}')
) AS v(event_code, body_template)
JOIN notification_event_type e ON e.code = v.event_code
CROSS JOIN channel_type c
ON CONFLICT (notification_event_type_id, channel_type_id, version) DO UPDATE
SET body_template = EXCLUDED.body_template,
    updated_at = CURRENT_TIMESTAMP;
