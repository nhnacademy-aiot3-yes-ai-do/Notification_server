-- RabbitMQ fan-out에서 template 미등록 같은 최종 실패도 delivery 이력으로 보존한다.
-- CREATED/PENDING/SENDING/SENT delivery는 실제 사용한 template이 반드시 있어야 한다.
ALTER TABLE notification_delivery
    ALTER COLUMN notification_template_id DROP NOT NULL;

ALTER TABLE notification_delivery
    ADD CONSTRAINT chk_delivery_template_required
        CHECK (
            status = 'FAILED'
            OR notification_template_id IS NOT NULL
        );
