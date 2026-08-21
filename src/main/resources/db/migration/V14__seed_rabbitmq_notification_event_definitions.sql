-- 신규 RabbitMQ 코드 계약(NotificationEventDefinition)의 DB 기준 event type을 보완한다.
-- 기존 관리 데이터는 수정하지 않고, 누락된 code만 추가한다.
INSERT INTO notification_event_type (code, display_name, description, target_type)
SELECT v.code, v.display_name, v.description, t.id
FROM (VALUES
    ('CULTIVATION_MEMBER_INVITED', '재배 멤버 초대', '재배 멤버가 초대됨', 'CULTIVATION'),
    ('INQUIRY_SUBMITTED', '문의 등록', '사용자 문의가 등록됨', 'INQUIRY'),
    ('LOGIN_FAILED', '로그인 실패', '사용자 로그인에 실패함', 'USER'),
    ('PASSWORD_CHANGED', '비밀번호 변경 완료', '사용자 비밀번호 변경이 완료됨', 'USER'),
    ('PASSWORD_CHANGE_FAILED', '비밀번호 변경 실패', '사용자 비밀번호 변경에 실패함', 'USER'),
    ('ACCOUNT_REACTIVATED', '계정 재활성화 완료', '사용자 계정 재활성화가 완료됨', 'USER'),
    ('ACCOUNT_REACTIVATION_FAILED', '계정 재활성화 실패', '사용자 계정 재활성화에 실패함', 'USER')
) AS v(code, display_name, description, target_code)
JOIN subscription_target_type t ON t.target_type = v.target_code
ON CONFLICT (code) DO NOTHING;

-- 이벤트가 실제 발송되려면 운영자가 이 유형별 구독 채널과 템플릿을 별도로 등록해야 한다.
