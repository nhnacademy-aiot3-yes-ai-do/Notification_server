package site.yesaido.notification_server.rabbitmq.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cultivation이 발행하는 {@code NotificationEvent<T>} Envelope의 수신 모델이다.
 * payload 타입만 이벤트별로 다르고, 공통 헤더는 이 레코드로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationEnvelope<T>(
        String eventId,
        String eventType,
        String producer,
        String targetType,
        Long targetId,
        String occurredAt,
        T payload
) {

    public UUID eventUuid() {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId");
        }
        return UUID.fromString(eventId);
    }

    public OffsetDateTime occurredAtOffsetDateTime() {
        if (occurredAt == null || occurredAt.isBlank()) {
            throw new IllegalArgumentException("occurredAt");
        }
        return OffsetDateTime.parse(occurredAt);
    }
}
