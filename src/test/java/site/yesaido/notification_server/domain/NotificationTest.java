package site.yesaido.notification_server.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.entity.Notification;
import site.yesaido.notification_server.entity.NotificationEventType;

class NotificationTest {

    @Test
    void 이벤트_유형과_식별자와_payload를_보관한다() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("sensorId", 3, "value", 28.5);
        NotificationEventType eventType = new NotificationEventType(
                "SENSOR_ERROR", "센서 오류", "센서 오류", null);

        Notification notification = new Notification(eventId, eventType, payload);

        assertEquals(eventId, notification.getSourceEventId());
        assertSame(eventType, notification.getEventType());
        assertSame(payload, notification.getEventPayload());
    }
}
