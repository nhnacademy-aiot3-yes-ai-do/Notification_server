package site.yesaido.notification_server.rabbitmq.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
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

    private static final Long TARGET_ID = 5L;
    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.parse("2026-08-23T12:34:56+09:00");

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
        Map<String, Object> storedPayload = Map.of(
                "cultivationId", 5L, "targetId", TARGET_ID, "occurredAt", OCCURRED_AT);
        Notification notification = notification(11L, eventId, eventType, payload);
        when(objectMapper.writeValueAsString(storedPayload))
                .thenReturn("{\"cultivationId\":5,\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}");
        when(notificationRepository.insertIfAbsent(eventId, 3L,
                "{\"cultivationId\":5,\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}"))
                .thenReturn(1);
        when(notificationRepository.findBySourceEventId(eventId)).thenReturn(Optional.of(notification));

        RabbitMqNotificationCreationResult result = service.createIfAbsent(
                eventId, eventType, TARGET_ID, OCCURRED_AT, payload);

        assertEquals(11L, result.notificationId());
        assertTrue(result.created());
    }

    @Test
    void 중복_이벤트는_기존_Notification을_반환하고_created를_false로_반환한다() throws Exception {
        // 중복 이벤트는 UNIQUE(source_event_id) + ON CONFLICT DO NOTHING의
        // 원자적 처리 결과(0)를 받아 기존 Notification만 재사용해야 한다.
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of();
        Map<String, Object> storedPayload = Map.of("targetId", TARGET_ID, "occurredAt", OCCURRED_AT);
        Notification notification = notification(11L, eventId, eventType, payload);
        when(objectMapper.writeValueAsString(storedPayload))
                .thenReturn("{\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}");
        when(notificationRepository.insertIfAbsent(eventId, 3L,
                "{\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}"))
                .thenReturn(0);
        when(notificationRepository.findBySourceEventId(eventId)).thenReturn(Optional.of(notification));

        RabbitMqNotificationCreationResult result = service.createIfAbsent(
                eventId, eventType, TARGET_ID, OCCURRED_AT, payload);

        assertFalse(result.created());
        assertEquals(11L, result.notificationId());
    }

    @Test
    void 이벤트_문맥을_eventPayload에_함께_저장한다() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of("cultivationId", 5L);
        Map<String, Object> storedPayload = Map.of(
                "cultivationId", 5L, "targetId", TARGET_ID, "occurredAt", OCCURRED_AT);
        Notification notification = notification(11L, eventId, eventType, payload);
        when(objectMapper.writeValueAsString(storedPayload))
                .thenReturn("{\"cultivationId\":5,\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}");
        when(notificationRepository.insertIfAbsent(eventId, 3L,
                "{\"cultivationId\":5,\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}"))
                .thenReturn(1);
        when(notificationRepository.findBySourceEventId(eventId)).thenReturn(Optional.of(notification));

        RabbitMqNotificationCreationResult result = service.createIfAbsent(
                eventId, eventType, TARGET_ID, OCCURRED_AT, payload);

        assertTrue(result.created());
        verify(notificationRepository).insertIfAbsent(eventId, 3L,
                "{\"cultivationId\":5,\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}");
    }

    @Test
    void 저장직후_Notification을_찾지_못하면_무결성오류로_처리한다() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of();
        when(objectMapper.writeValueAsString(Map.of("targetId", TARGET_ID, "occurredAt", OCCURRED_AT)))
                .thenReturn("{\"targetId\":5,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}");
        when(notificationRepository.findBySourceEventId(eventId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.createIfAbsent(eventId, eventType, TARGET_ID, OCCURRED_AT, payload));
    }

    @Test
    void payload_JSON_변환에_실패하면_DB에_저장하지_않는다() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of("unsupported", new Object());
        Map<String, Object> storedPayload = Map.of(
                "unsupported", payload.get("unsupported"),
                "targetId", TARGET_ID,
                "occurredAt", OCCURRED_AT);
        when(objectMapper.writeValueAsString(storedPayload))
                .thenThrow(new JsonProcessingException("serialization failed") { });

        assertThrows(IllegalArgumentException.class,
                () -> service.createIfAbsent(eventId, eventType, TARGET_ID, OCCURRED_AT, payload));
        verify(objectMapper).writeValueAsString(storedPayload);
    }

    @Test
    void targetId가_없으면_저장하지_않는다() {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of();
        assertThrows(IllegalArgumentException.class,
                () -> service.createIfAbsent(
                        eventId, eventType, null, OCCURRED_AT, payload));
    }

    @Test
    void occurredAt이_없으면_저장하지_않는다() {
        UUID eventId = UUID.randomUUID();
        NotificationEventType eventType = eventType(3L);
        Map<String, Object> payload = Map.of();
        assertThrows(IllegalArgumentException.class,
                () -> service.createIfAbsent(
                        eventId, eventType, TARGET_ID, null, payload));
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
