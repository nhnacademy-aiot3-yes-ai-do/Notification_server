-- 새 RabbitMQ 저장 경로가 만든 Delivery는 외부 Provider 발송 전 상태(CREATED)로 보관한다.
-- 기존 row의 CHECK 검증은 V21로 분리해 대형 테이블에 대한 즉시 전수 스캔을 피한다.
ALTER TABLE notification_delivery
    DROP CONSTRAINT chk_delivery_status;

ALTER TABLE notification_delivery
    ADD CONSTRAINT chk_delivery_status
        CHECK (status IN ('CREATED', 'PENDING', 'SENDING', 'SENT', 'FAILED'))
        NOT VALID;
