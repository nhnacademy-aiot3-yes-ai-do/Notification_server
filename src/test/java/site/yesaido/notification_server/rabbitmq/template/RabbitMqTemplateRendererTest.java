package site.yesaido.notification_server.rabbitmq.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.exception.template.NotificationTemplateVariableMissingException;

class RabbitMqTemplateRendererTest {

    private final RabbitMqTemplateRenderer renderer = new RabbitMqTemplateRenderer(new ObjectMapper());

    @Test
    void rabbitMqPayload의_중첩변수를_렌더링한다() {
        String rendered = renderer.render("{{cultivation.name}} 수확량: {{harvestQuantity}}g", Map.of(
                "cultivation", Map.of("name", "토마토 A동"),
                "harvestQuantity", new BigDecimal("12.5")));

        assertThat(rendered).isEqualTo("토마토 A동 수확량: 12.5g");
    }

    @Test
    void payload에_없는_변수는_예외를_던진다() {
        assertThatThrownBy(() -> renderer.render("{{missing}}", Map.of()))
                .isInstanceOf(NotificationTemplateVariableMissingException.class);
    }
}
