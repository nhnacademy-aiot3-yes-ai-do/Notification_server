package site.yesaido.notification_server.rabbitmq.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.core.ResolvableType;
import site.yesaido.notification_server.rabbitmq.config.RabbitListenerConfig;

class NotificationEnvelopeTest {

    private static final String HARVEST_ENVELOPE_JSON = """
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

    @Test
    void 컬티베이션_수확_Envelope_JSON을_역직렬화한다() throws Exception {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new ObjectMapper()
                .readValue(HARVEST_ENVELOPE_JSON, new TypeReference<>() { });

        assertThat(envelope.eventType()).isEqualTo("HARVEST_COMPLETED");
        assertThat(envelope.producer()).isEqualTo("cultivation-server");
        assertThat(envelope.targetType()).isEqualTo("CULTIVATION");
        assertThat(envelope.targetId()).isEqualTo(3L);
        assertThat(envelope.payload().cultivationName()).isEqualTo("광주");
        assertThat(envelope.payload().harvestWeight()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(envelope.eventUuid())
                .isEqualTo(UUID.fromString("7a5bc0a0-b4a7-4c50-b2e2-4d238c234487"));
        assertThat(envelope.occurredAtOffsetDateTime())
                .isEqualTo(OffsetDateTime.parse("2026-08-27T14:15:30.123+09:00"));
    }

    @Test
    void 알_수_없는_필드는_무시한다() throws Exception {
        String json = """
                {
                  "eventId": "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
                  "eventType": "HARVEST_COMPLETED",
                  "producer": "cultivation-server",
                  "targetType": "CULTIVATION",
                  "targetId": 3,
                  "occurredAt": "2026-08-27T14:15:30+09:00",
                  "extraHeader": "ignored",
                  "payload": {
                    "cultivationName": "에브리",
                    "harvestWeight": 1.5,
                    "extraPayload": true
                  }
                }
                """;

        NotificationEnvelope<HarvestCompletedPayload> envelope = new ObjectMapper()
                .readValue(json, new TypeReference<>() { });

        assertThat(envelope.payload().cultivationName()).isEqualTo("에브리");
        assertThat(envelope.payload().harvestWeight()).isEqualByComparingTo("1.5");
    }

    @Test
    void Listener_INFERRED_타입으로_Envelope를_변환한다() {
        JacksonJsonMessageConverter converter = new RabbitListenerConfig().nonAuthEventMessageConverter();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(ResolvableType
                .forClassWithGenerics(NotificationEnvelope.class, HarvestCompletedPayload.class)
                .getType());
        Message message = new Message(HARVEST_ENVELOPE_JSON.getBytes(StandardCharsets.UTF_8), properties);

        Object converted = converter.fromMessage(message);

        assertThat(converted).isInstanceOf(NotificationEnvelope.class);
        @SuppressWarnings("unchecked")
        NotificationEnvelope<HarvestCompletedPayload> envelope =
                (NotificationEnvelope<HarvestCompletedPayload>) converted;
        assertThat(envelope.targetId()).isEqualTo(3L);
        assertThat(envelope.payload()).isInstanceOf(HarvestCompletedPayload.class);
        assertThat(envelope.payload().harvestWeight()).isEqualByComparingTo("10");
    }

    @Test
    void eventId가_없으면_UUID로_읽지_않는다() {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new NotificationEnvelope<>(
                " ", "HARVEST_COMPLETED", "cultivation-server", "CULTIVATION", 3L,
                "2026-08-27T14:15:30+09:00", new HarvestCompletedPayload("광주", BigDecimal.TEN));

        assertThatThrownBy(envelope::eventUuid).isInstanceOf(IllegalArgumentException.class);
    }
}
