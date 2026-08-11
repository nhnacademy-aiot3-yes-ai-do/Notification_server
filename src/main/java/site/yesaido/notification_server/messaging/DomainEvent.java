package site.yesaido.notification_server.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 서비스 간 RabbitMQ 이벤트의 공통 외피(envelope).
 * 개별 서비스의 상세 값은 payload에 담고, Notification은 이 공통 필드로
 * 중복 제거·대상 선택·템플릿 선택을 수행한다.
 */
public record DomainEvent(
        UUID eventId,
        String eventType,
        String producer,
        String targetType,
        Long targetId,
        LocalDateTime occurredAt,
        JsonNode payload
) {

    public void validate() {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        requireText(eventType, "eventType");
        requireText(producer, "producer");
        requireText(targetType, "targetType");
        if (targetId == null || targetId < 1) {
            throw new IllegalArgumentException("targetId must be a positive number");
        }
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
