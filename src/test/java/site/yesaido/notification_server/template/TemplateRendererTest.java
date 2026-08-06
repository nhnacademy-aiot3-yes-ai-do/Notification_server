package site.yesaido.notification_server.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.exception.TemplateRenderingException;
import site.yesaido.notification_server.messaging.DomainEvent;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rendersPayloadAndEnvelopeVariables() {
        DomainEvent event = event("""
                {
                  "cultivationName": "느타리 1호",
                  "sensor": {"value": 27.5}
                }
                """);

        String result = renderer.render(
                "{{cultivationName}}의 값은 {{sensor.value}}, 대상은 {{targetId}}", event);

        assertThat(result).isEqualTo("느타리 1호의 값은 27.5, 대상은 101");
    }

    @Test
    void rejectsMissingVariable() {
        DomainEvent event = event("{}");

        assertThatThrownBy(() -> renderer.render("{{missing}}", event))
                .isInstanceOf(TemplateRenderingException.class)
                .hasMessageContaining("missing");
    }

    private DomainEvent event(String payload) {
        try {
            return new DomainEvent(
                    UUID.randomUUID(),
                    "SENSOR_ERROR",
                    "rule-server",
                    "CULTIVATION",
                    101L,
                    OffsetDateTime.parse("2026-07-31T10:00:00+09:00"),
                    objectMapper.readTree(payload));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
