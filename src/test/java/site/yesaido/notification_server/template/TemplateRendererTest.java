package site.yesaido.notification_server.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.exception.template.NotificationTemplateVariableMissingException;
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
                .isInstanceOf(NotificationTemplateVariableMissingException.class)
                .hasMessage("알림 템플릿에 필요한 변수가 없습니다");
    }

    @Test
    void rendersAgreedHarvestWeightVariable() {
        DomainEvent event = event("""
                {
                  "cultivationName": "Cultivation1",
                  "harvestWeight": 2.5
                }
                """);

        String result = renderer.render(
                "[수확 완료] {{cultivationName}}의 수확량: {{harvestWeight}}g", event);

        assertThat(result).isEqualTo("[수확 완료] Cultivation1의 수확량: 2.5g");
    }

    private DomainEvent event(String payload) {
        try {
            return new DomainEvent(
                    UUID.randomUUID(),
                    "SENSOR_ERROR",
                    "rule-server",
                    "CULTIVATION",
                    101L,
                    LocalDateTime.parse("2026-07-31T10:00:00"),
                    objectMapper.readTree(payload));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
