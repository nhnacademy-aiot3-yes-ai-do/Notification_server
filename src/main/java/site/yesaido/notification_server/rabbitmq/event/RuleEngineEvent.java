package site.yesaido.notification_server.rabbitmq.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class RuleEngineEvent {

    public record SensorDataDto (
            String place,
            String location,
            String deviceModel,
            String deviceName,
            String deviceEui,
            String sensorType,
            Double value,
            @JsonProperty("time")
            LocalDateTime measuredAt,
            String unit,
            long cultivationId
    ) {}

    // 임계값 초과 및 회복 알림
    public record ThresholdStatusChangedEvent (
            SensorDataDto sensorData,
            ThresholdStatus status,
            OffsetDateTime occurredAt
    ) {}

    public enum ThresholdStatus {
        EXCEEDED,
        RECOVERED
    }

    // 자동화 키고 끄고 알림
    public record AutomationStateChangedEvent (
            long cultivationId,
            String actuatorType,
            String message,
            boolean enabled,
            OffsetDateTime occurredAt
    ) {}
}
