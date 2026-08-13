package site.yesaido.notification_server.rabbitmq.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;

class RabbitMqNotificationPayloadProcessorTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-12T10:15:30+09:00");

    private final RuleEngineNotificationProcessor ruleEngineProcessor = new RuleEngineNotificationProcessor();
    private final AiNotificationProcessor aiProcessor = new AiNotificationProcessor();
    private final CultivationNotificationProcessor cultivationProcessor = new CultivationNotificationProcessor();
    private final UserNotificationProcessor userProcessor = new UserNotificationProcessor();

    @Test
    void 임계값이상_이벤트를_기존템플릿변수_전용_payload로_변환한다() {
        RabbitMqNotificationCommand command = ruleEngineProcessor.process(
                new RuleEngineEvent.ThresholdStatusChangedEvent(UUID.randomUUID(),
                        new RuleEngineEvent.SensorDataDto("토마토 A동", "A-1", "T100", "온도 센서", "eui",
                                "TEMPERATURE", 31.2, OCCURRED_AT, "°C", 7L),
                        RuleEngineEvent.ThresholdStatus.EXCEEDED, OCCURRED_AT));

        assertThat(command.eventCode()).isEqualTo("ENVIRONMENT_THRESHOLD_BREACHED");
        assertThat(command.payload()).isEqualTo(Map.of(
                "cultivationName", "토마토 A동",
                "sensorType", "TEMPERATURE",
                "currentValue", 31.2,
                "unit", "°C",
                "thresholdMin", "미제공",
                "thresholdMax", "미제공"));
    }

    @Test
    void 자동화상태변경은_실패가_아닌_제어성공_이벤트로_변환한다() {
        RabbitMqNotificationCommand command = ruleEngineProcessor.process(
                new RuleEngineEvent.AutomationStateChangedEvent(UUID.randomUUID(), 7L, "FAN", "팬 제어 완료", true, OCCURRED_AT));

        assertThat(command.eventCode()).isEqualTo("ACTUATOR_CONTROL_SUCCEEDED");
        assertThat(command.payload()).isEqualTo(Map.of(
                "cultivationName", "미제공",
                "deviceName", "FAN",
                "controlType", "ON"));
    }

    @Test
    void 수확량을_V11_템플릿의_harvestWeight로_변환한다() {
        RabbitMqNotificationCommand command = cultivationProcessor.process(
                new CultivationEvent.HarvestCompletedEvent(UUID.randomUUID(), 2L, 7L, "토마토 A동", 8L,
                        new BigDecimal("12.50"), OCCURRED_AT));

        assertThat(command.payload()).isEqualTo(Map.of(
                "cultivationName", "토마토 A동",
                "harvestWeight", new BigDecimal("12.50")));
    }

    @Test
    void AI와_문의와_로그인이_기존템플릿변수만_포함한_payload로_변환된다() {
        RabbitMqNotificationCommand feedback = aiProcessor.process(
                new AiEvent.DailyFeedbackGeneratedEvent(UUID.randomUUID(), 2L, 7L, "토마토 A동", "url", "성장 중", OCCURRED_AT));
        RabbitMqNotificationCommand inquiry = userProcessor.process(
                new UserEvent.InquirySubmittedEvent(UUID.randomUUID(), 2L, java.util.List.of(1L), 9L,
                        "문의 제목", "GENERAL", "url", UserEvent.InquiryType.ANSWER, OCCURRED_AT));
        RabbitMqNotificationCommand login = userProcessor.process(
                new UserEvent.UserLoginAttemptedEvent(UUID.randomUUID(), 2L, "tester", true, "Seoul", OCCURRED_AT));

        assertThat(feedback.payload()).isEqualTo(Map.of("cultivationName", "토마토 A동", "feedbackSummary", "성장 중"));
        assertThat(inquiry.payload()).isEqualTo(Map.of("inquiryTitle", "문의 제목"));
        assertThat(login.payload()).isEqualTo(Map.of("provider", "미제공"));
    }
}
