package site.yesaido.notification_server.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.exception.messaging.InvalidDomainEventException;

class DomainEventParserTest {

    private DomainEventParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        parser = new DomainEventParser(objectMapper);
    }

    @Test
    void 합의된_수확완료_JSON을_공통이벤트로_변환한다() {
        String eventId = "b3f1c2a4-6c0a-4c9d-a1ad-123456789abc";
        String message = """
                {
                  "eventId": "%s",
                  "eventType": "HARVEST_COMPLETED",
                  "producer": "cultivation-server",
                  "targetType": "CULTIVATION",
                  "targetId": 1,
                  "occurredAt": "2026-08-11T14:30:00",
                  "payload": {
                    "cultivationId": 1,
                    "cultivationName": "Cultivation1",
                    "userId": 100,
                    "harvestId": 5,
                    "harvestWeight": 2.5,
                    "harvestedAt": "2026-08-11T14:30:00"
                  }
                }
                """.formatted(eventId);

        DomainEvent event = parser.parse(message);

        assertEquals(UUID.fromString(eventId), event.eventId());
        assertEquals("HARVEST_COMPLETED", event.eventType());
        assertEquals("cultivation-server", event.producer());
        assertEquals("CULTIVATION", event.targetType());
        assertEquals(1L, event.targetId());
        assertEquals(LocalDateTime.parse("2026-08-11T14:30:00"), event.occurredAt());
        assertEquals("Cultivation1", event.payload().get("cultivationName").textValue());
        assertEquals(5L, event.payload().get("harvestId").longValue());
        assertEquals(2.5, event.payload().get("harvestWeight").doubleValue());
        assertEquals("2026-08-11T14:30:00", event.payload().get("harvestedAt").textValue());
    }

    @Test
    void 임시_환경이상_JSON에서_템플릿에_필요한_값을_읽는다() {
        String message = """
                {
                  "eventId": "e4f5216e-41ae-4b1c-becf-5c3a94dc3311",
                  "eventType": "ENVIRONMENT_THRESHOLD_BREACHED",
                  "producer": "rule-engine",
                  "targetType": "CULTIVATION",
                  "targetId": 101,
                  "occurredAt": "2026-08-06T09:30:00",
                  "payload": {
                    "cultivationName": "느타리 1번",
                    "sensorType": "TEMPERATURE",
                    "currentValue": 28.5,
                    "unit": "°C",
                    "thresholdMin": 18,
                    "thresholdMax": 22
                  }
                }
                """;

        DomainEvent event = parser.parse(message);

        assertEquals("ENVIRONMENT_THRESHOLD_BREACHED", event.eventType());
        assertEquals("rule-engine", event.producer());
        assertEquals("느타리 1번", event.payload().get("cultivationName").textValue());
        assertEquals(28.5, event.payload().get("currentValue").doubleValue());
        assertEquals(22, event.payload().get("thresholdMax").intValue());
    }

    @Test
    void 임시_AI_피드백완료_JSON에서_요약을_읽는다() {
        String message = """
                {
                  "eventId": "e36fb8a6-7356-4cc3-b098-62480be40a63",
                  "eventType": "DAILY_FEEDBACK_COMPLETED",
                  "producer": "ai-service",
                  "targetType": "CULTIVATION",
                  "targetId": 101,
                  "occurredAt": "2026-08-06T10:00:00",
                  "payload": {
                    "cultivationName": "느타리 1번",
                    "feedbackId": 82,
                    "feedbackSummary": "습도 관리가 안정적입니다."
                  }
                }
                """;

        DomainEvent event = parser.parse(message);

        assertEquals("DAILY_FEEDBACK_COMPLETED", event.eventType());
        assertEquals(82L, event.payload().get("feedbackId").longValue());
        assertEquals("습도 관리가 안정적입니다.",
                event.payload().get("feedbackSummary").textValue());
    }

    @Test
    void 공통필드가_유효하면_아직_확정되지_않은_이벤트코드도_파싱한다() {
        String message = """
                {
                  "eventId": "2e7c2e2e-6c0a-4c9d-a1ad-123456789abc",
                  "eventType": "FUTURE_EVENT",
                  "producer": "future-service",
                  "targetType": "FUTURE_TARGET",
                  "targetId": 1,
                  "occurredAt": "2026-07-30T10:00:00",
                  "payload": {}
                }
                """;

        DomainEvent event = parser.parse(message);

        assertEquals("FUTURE_EVENT", event.eventType());
        assertEquals("FUTURE_TARGET", event.targetType());
    }

    @Test
    void 필수필드가_누락되면_거부한다() {
        String message = """
                {
                  "eventId": "2e7c2e2e-6c0a-4c9d-a1ad-123456789abc",
                  "producer": "cultivation-service",
                  "targetType": "CULTIVATION",
                  "targetId": 101,
                  "occurredAt": "2026-07-30T10:00:00",
                  "payload": {}
                }
                """;

        InvalidDomainEventException exception = assertThrows(
                InvalidDomainEventException.class,
                () -> parser.parse(message)
        );

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("eventType is required", exception.getCause().getMessage());
    }

    @Test
    void targetId가_양수가_아니면_거부한다() {
        String message = """
                {
                  "eventId": "2e7c2e2e-6c0a-4c9d-a1ad-123456789abc",
                  "eventType": "HARVEST_COMPLETED",
                  "producer": "cultivation-service",
                  "targetType": "CULTIVATION",
                  "targetId": 0,
                  "occurredAt": "2026-07-30T10:00:00",
                  "payload": {}
                }
                """;

        InvalidDomainEventException exception = assertThrows(
                InvalidDomainEventException.class,
                () -> parser.parse(message)
        );

        assertEquals("targetId must be a positive number", exception.getCause().getMessage());
    }

    @Test
    void 빈_메시지와_잘못된_JSON을_거부한다() {
        assertThrows(InvalidDomainEventException.class, () -> parser.parse(" "));
        assertThrows(
                InvalidDomainEventException.class,
                () -> parser.parse("{ \"eventType\": ")
        );
    }
}
