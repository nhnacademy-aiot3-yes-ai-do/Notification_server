package site.yesaido.notification_server.rabbitmq.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.entity.Notification;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.repository.NotificationRepository;

@Service
@RequiredArgsConstructor
public class RabbitMqNotificationCreationService {

    private static final String TARGET_ID = "targetId";
    private static final String OCCURRED_AT = "occurredAt";

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RabbitMqNotificationCreationResult createIfAbsent(
            UUID eventId,
            NotificationEventType eventType,
            Long targetId,
            OffsetDateTime occurredAt,
            Map<String, Object> payload) {
        if (targetId == null) {
            throw new IllegalArgumentException("RabbitMQ notification targetId는 필수입니다");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("RabbitMQ notification occurredAt은 필수입니다");
        }
        boolean created = notificationRepository.insertIfAbsent(
                eventId, eventType.getId(), writePayload(payload, targetId, occurredAt)) == 1;
        return result(eventId, created);
    }

    private RabbitMqNotificationCreationResult result(UUID eventId, boolean created) {
        Notification notification = notificationRepository.findBySourceEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("notification을 조회할 수 없습니다: " + eventId));
        return new RabbitMqNotificationCreationResult(notification.getId(), created);
    }

    private String writePayload(Map<String, Object> payload, Long targetId, OffsetDateTime occurredAt) {
        Map<String, Object> eventPayload = new LinkedHashMap<>(payload);
        eventPayload.put(TARGET_ID, targetId);
        eventPayload.put(OCCURRED_AT, occurredAt);
        try {
            return objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("RabbitMQ notification payload을 JSON으로 변환할 수 없습니다", exception);
        }
    }

    public record RabbitMqNotificationCreationResult(Long notificationId, boolean created) {

    }
}
