package site.yesaido.notification_server.rabbitmq.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.core.ResolvableType;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.config.RabbitListenerConfig;
import site.yesaido.notification_server.rabbitmq.processor.CultivationNotificationProcessor;

class CultivationEnvelopeContractTest {

    private static final String HARVEST_JSON = """
            {
              "eventId": "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
              "eventType": "HARVEST_COMPLETED",
              "producer": "cultivation-server",
              "targetType": "CULTIVATION",
              "targetId": 3,
              "occurredAt": "2026-08-27T14:15:30.123+09:00",
              "payload": {
                "cultivationName": "광주",
                "harvestWeight": 10
              }
            }
            """;

    private static final String MEMBER_JSON = """
            {
              "eventId": "8a5bc0a0-b4a7-4c50-b2e2-4d238c234488",
              "eventType": "MEMBER_ADDED",
              "producer": "cultivation-server",
              "targetType": "USER",
              "targetId": 21,
              "occurredAt": "2026-08-27T14:15:30.123+09:00",
              "payload": {
                "cultivationId": 3,
                "cultivationName": "광주",
                "role": "MEMBER"
              }
            }
            """;

    private final CultivationNotificationProcessor processor = new CultivationNotificationProcessor();
    private final JacksonJsonMessageConverter converter = new RabbitListenerConfig().nonAuthEventMessageConverter();

    @Test
    void 수확_JSON을_Envelope에서_Command까지_변환한다() throws Exception {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new ObjectMapper()
                .readValue(HARVEST_JSON, new TypeReference<>() { });
        RabbitMqNotificationCommand command = processor.processHarvestCompleted(envelope);

        assertThat(command.eventId()).isEqualTo(UUID.fromString("7a5bc0a0-b4a7-4c50-b2e2-4d238c234487"));
        assertThat(command.eventCode()).isEqualTo("HARVEST_COMPLETED");
        assertThat(command.targetType()).isEqualTo("CULTIVATION");
        assertThat(command.targetId()).isEqualTo(3L);
        assertThat(command.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-08-27T14:15:30.123+09:00"));
        assertThat(command.payload()).isEqualTo(Map.of(
                "cultivationName", "광주",
                "harvestWeight", new BigDecimal("10")));
    }

    @Test
    void 멤버추가_JSON을_Envelope에서_USER_Command까지_변환한다() throws Exception {
        NotificationEnvelope<MemberAddedPayload> envelope = new ObjectMapper()
                .readValue(MEMBER_JSON, new TypeReference<>() { });
        RabbitMqNotificationCommand command = processor.processMemberAdded(envelope);

        assertThat(command.eventId()).isEqualTo(UUID.fromString("8a5bc0a0-b4a7-4c50-b2e2-4d238c234488"));
        assertThat(command.eventCode()).isEqualTo("MEMBER_ADDED");
        assertThat(command.targetType()).isEqualTo("USER");
        assertThat(command.targetId()).isEqualTo(21L);
        assertThat(command.payload()).isEqualTo(Map.of(
                "cultivationId", 3L,
                "cultivationName", "광주",
                "role", "MEMBER"));
    }

    @Test
    void Listener가_수확_JSON을_INFERRED_Envelope로_받는다() {
        Object converted = converter.fromMessage(jsonMessage(
                HARVEST_JSON,
                ResolvableType.forClassWithGenerics(NotificationEnvelope.class, HarvestCompletedPayload.class)));

        assertThat(converted).isInstanceOf(NotificationEnvelope.class);
        @SuppressWarnings("unchecked")
        NotificationEnvelope<HarvestCompletedPayload> envelope =
                (NotificationEnvelope<HarvestCompletedPayload>) converted;
        assertThat(envelope.eventType()).isEqualTo("HARVEST_COMPLETED");
        assertThat(envelope.payload().harvestWeight()).isEqualByComparingTo("10");
    }

    @Test
    void Listener가_멤버추가_JSON을_INFERRED_Envelope로_받는다() {
        Object converted = converter.fromMessage(jsonMessage(
                MEMBER_JSON,
                ResolvableType.forClassWithGenerics(NotificationEnvelope.class, MemberAddedPayload.class)));

        assertThat(converted).isInstanceOf(NotificationEnvelope.class);
        @SuppressWarnings("unchecked")
        NotificationEnvelope<MemberAddedPayload> envelope =
                (NotificationEnvelope<MemberAddedPayload>) converted;
        assertThat(envelope.eventType()).isEqualTo("MEMBER_ADDED");
        assertThat(envelope.targetType()).isEqualTo("USER");
        assertThat(envelope.targetId()).isEqualTo(21L);
        assertThat(envelope.payload().role()).isEqualTo("MEMBER");
    }

    private Message jsonMessage(String json, ResolvableType type) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(type.getType());
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
