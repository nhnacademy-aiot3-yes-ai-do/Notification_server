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
        Map<String, Object> payload = Map.of();
        assertThatThrownBy(() -> renderer.render("{{missing}}", payload))
                .isInstanceOf(NotificationTemplateVariableMissingException.class);
    }

    @Test
    void 일일피드백_템플릿은_공개_feedbackUrl을_렌더링한다() {
        String template = """
                [AI 일일 피드백] {{cultivationName}}의 오늘 피드백이 생성되었습니다.
                {{feedbackSummary}}
                {{feedbackUrl}}""";

        String rendered = renderer.render(template, Map.of(
                "cultivationName", "테스트 1번",
                "feedbackSummary", "오늘 환경유지율은 87%입니다.",
                "feedbackUrl", "https://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31"));

        assertThat(rendered).isEqualTo("""
                [AI 일일 피드백] 테스트 1번의 오늘 피드백이 생성되었습니다.
                오늘 환경유지율은 87%입니다.
                https://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31""");
    }
}
