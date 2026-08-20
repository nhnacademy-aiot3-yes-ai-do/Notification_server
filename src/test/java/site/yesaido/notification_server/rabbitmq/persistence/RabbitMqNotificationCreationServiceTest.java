package site.yesaido.notification_server.rabbitmq.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.notification_server.entity.Notification;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.entity.SubscriptionTargetType;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationCreationService.RabbitMqNotificationCreationResult;
import site.yesaido.notification_server.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class RabbitMqNotificationCreationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ObjectMapper objectMapper;

    private RabbitMqNotificationCreationService service;

    @BeforeEach
    void setUp() {
        service = new RabbitMqNotificationCreationService(notificationRepository, objectMapper);
    }

    @Test
    void 처음_수신한_이벤트는_Notification을_생성하고_created를_true로_반환한다() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of("cultivationId", 5L);
        Notification notification = notification(11L, eventId, eventType, payload);
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"cultivationId\":5}");
        when(notificationRepository.insertIfAbsent(eventId, 3L, "{\"cultivationId\":5}"))
                .thenReturn(1);
        when(notificationRepository.findBySourceEventId(eventId)).thenReturn(Optional.of(notification));

        RabbitMqNotificationCreationResult result = service.createIfAbsent(eventId, eventType, payload);

        assertEquals(11L, result.notificationId());
        assertTrue(result.created());
    }

    @Test
    void 중복_이벤트는_기존_Notification을_반환하고_created를_false로_반환한다() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of();
        Notification notification = notification(11L, eventId, eventType, payload);
        when(objectMapper.writeValueAsString(payload)).thenReturn("{}");
        when(notificationRepository.insertIfAbsent(eventId, 3L, "{}")).thenReturn(0);
        when(notificationRepository.findBySourceEventId(eventId)).thenReturn(Optional.of(notification));

        RabbitMqNotificationCreationResult result = service.createIfAbsent(eventId, eventType, payload);

        assertFalse(result.created());
        assertEquals(11L, result.notificationId());
    }

    @Test
    void 저장직후_Notification을_찾지_못하면_무결성오류로_처리한다() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of();
        when(objectMapper.writeValueAsString(payload)).thenReturn("{}");
        when(notificationRepository.findBySourceEventId(eventId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.createIfAbsent(eventId, eventType, payload));
    }

    @Test
    void payload_JSON_변환에_실패하면_DB에_저장하지_않는다() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of("unsupported", new Object());
        when(objectMapper.writeValueAsString(payload))
                .thenThrow(new JsonProcessingException("serialization failed") { });

        assertThrows(IllegalArgumentException.class,
                () -> service.createIfAbsent(eventId, eventType, payload));
        verify(objectMapper).writeValueAsString(payload);
    }

    private NotificationEventType eventType(Long id) {
        NotificationEventType eventType = new NotificationEventType(
                "HARVEST_COMPLETED", "수확 완료", "수확 완료", new SubscriptionTargetType("CULTIVATION", "재배"));
        ReflectionTestUtils.setField(eventType, "id", id);
        return eventType;
    }

    private Notification notification(Long id, UUID eventId, NotificationEventType eventType,
                                      Map<String, Object> payload) {
        Notification notification = new Notification(eventId, eventType, payload);
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }
}
