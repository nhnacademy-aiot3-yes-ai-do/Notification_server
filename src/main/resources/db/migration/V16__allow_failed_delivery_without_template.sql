-- RabbitMQ fan-out에서 template 미등록 같은 최종 실패도 delivery 이력으로 보존한다.
ALTER TABLE notification_delivery
    ALTER COLUMN notification_template_id DROP NOT NULL;
