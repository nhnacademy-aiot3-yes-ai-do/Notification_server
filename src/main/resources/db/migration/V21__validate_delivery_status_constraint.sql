-- V13에서 신규 쓰기에 적용된 Delivery status CHECK를 기존 row에도 검증한다.
ALTER TABLE notification_delivery
    VALIDATE CONSTRAINT chk_delivery_status;
