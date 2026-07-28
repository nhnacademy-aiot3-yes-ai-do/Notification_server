-- 템플릿은 채널별 Delivery에서 선택한다.
-- 기존 데이터가 있는 환경에서도 기존 Notification의 템플릿을 우선 이관한다.

ALTER TABLE notification_delivery
    ADD COLUMN notification_template_id BIGINT;

UPDATE notification_delivery d
SET notification_template_id = n.notification_template_id
FROM notification n
WHERE d.notification_id = n.id;

ALTER TABLE notification_delivery
    ALTER COLUMN notification_template_id SET NOT NULL;

ALTER TABLE notification_delivery
    ADD CONSTRAINT fk_delivery_template
        FOREIGN KEY (notification_template_id)
        REFERENCES notification_template (id);

ALTER TABLE notification
    DROP CONSTRAINT fk_notification_template;

ALTER TABLE notification
    DROP COLUMN notification_template_id;

CREATE INDEX idx_delivery_template
    ON notification_delivery (notification_template_id);
