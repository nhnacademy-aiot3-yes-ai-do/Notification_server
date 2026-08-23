-- 기간별 알림 집계에 필요한 원본 이벤트 대상과 발생 시각을 보존한다.
-- 기존 행에는 대상 정보를 복원할 근거가 없으므로 두 컬럼을 NULL로 유지한다.
ALTER TABLE notification
    ADD COLUMN target_id BIGINT,
    ADD COLUMN occurred_at TIMESTAMPTZ;

-- 신규 이벤트 문맥은 대상과 발생 시각이 함께 저장되어야 한다.
-- 기존 행은 두 값이 모두 NULL이므로 제약조건을 만족한다.
ALTER TABLE notification
    ADD CONSTRAINT chk_notification_event_context_pair
    CHECK (
        (target_id IS NULL AND occurred_at IS NULL)
        OR (target_id IS NOT NULL AND occurred_at IS NOT NULL)
    ) NOT VALID;
