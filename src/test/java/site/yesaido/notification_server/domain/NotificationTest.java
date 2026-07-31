package site.yesaido.notification_server.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void 이벤트_식별자와_payload를_보관한다() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("sensorId", 3, "value", 28.5);

        Notification notification = new Notification(eventId, payload);

        assertEquals(eventId, notification.getSourceEventId());
        assertSame(payload, notification.getEventPayload());
    }
}
