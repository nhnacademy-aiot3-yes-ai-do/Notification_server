-- Delivery를 외부 Provider 호출 전에 SENDING으로 선점해 Consumer와 복구 스케줄러의
-- 동시 발송을 막는다. 이미 적용된 V1 제약은 변경하지 않고 새 Migration으로 교체한다.
ALTER TABLE notification_delivery
    DROP CONSTRAINT chk_delivery_status;

ALTER TABLE notification_delivery
    ADD CONSTRAINT chk_delivery_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'));
