package com.ecosphere.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void 이벤트_식별자_payload_메시지를_보관한다() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("sensorId", 3, "value", 28.5);

        Notification notification = new Notification(eventId, payload, "센서 오류가 발생했습니다.");

        assertEquals(eventId, notification.getSourceEventId());
        assertSame(payload, notification.getEventPayload());
        assertEquals("센서 오류가 발생했습니다.", notification.getMessage());
    }
}
