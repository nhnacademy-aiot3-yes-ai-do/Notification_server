package site.yesaido.notification_server.rabbitmq.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.event.HarvestCompletedPayload;
import site.yesaido.notification_server.rabbitmq.event.MemberAddedPayload;
import site.yesaido.notification_server.rabbitmq.event.NotificationEnvelope;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;

class RabbitMqNotificationPayloadProcessorTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-12T10:15:30+09:00");

    private final RuleEngineNotificationProcessor ruleEngineProcessor = new RuleEngineNotificationProcessor();
    private final AiNotificationProcessor aiProcessor = new AiNotificationProcessor("https://yes-nhn.site");
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
    void 수확_Envelope를_targetId와_harvestWeight로_변환한다() {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new NotificationEnvelope<>(
                "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
                "HARVEST_COMPLETED",
                "cultivation-server",
                "CULTIVATION",
                3L,
                "2026-08-27T14:15:30.123+09:00",
                new HarvestCompletedPayload("광주", new BigDecimal("10")));

        RabbitMqNotificationCommand command = cultivationProcessor.processHarvestCompleted(envelope);

        assertThat(command.eventCode()).isEqualTo("HARVEST_COMPLETED");
        assertThat(command.targetType()).isEqualTo("CULTIVATION");
        assertThat(command.targetId()).isEqualTo(3L);
        assertThat(command.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-08-27T14:15:30.123+09:00"));
        assertThat(command.payload()).isEqualTo(Map.of(
                "cultivationName", "광주",
                "harvestWeight", new BigDecimal("10")));
    }

    @Test
    void 수확_Envelope에_수확량이_없으면_계약_예외를_던진다() {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new NotificationEnvelope<>(
                "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
                "HARVEST_COMPLETED",
                "cultivation-server",
                "CULTIVATION",
                3L,
                "2026-08-27T14:15:30+09:00",
                new HarvestCompletedPayload("광주", null));

        assertThatThrownBy(() -> cultivationProcessor.processHarvestCompleted(envelope))
                .isInstanceOf(site.yesaido.notification_server.rabbitmq.exception.RabbitMqHarvestQuantityMissingException.class);
    }

    @Test
    void 수확_Envelope의_eventId가_잘못된_UUID면_예외를_던진다() {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new NotificationEnvelope<>(
                "not-a-uuid",
                "HARVEST_COMPLETED",
                "cultivation-server",
                "CULTIVATION",
                3L,
                "2026-08-27T14:15:30+09:00",
                new HarvestCompletedPayload("광주", BigDecimal.TEN));

        assertThatThrownBy(() -> cultivationProcessor.processHarvestCompleted(envelope))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수확_Envelope의_occurredAt이_잘못된_시각이면_예외를_던진다() {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new NotificationEnvelope<>(
                "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
                "HARVEST_COMPLETED",
                "cultivation-server",
                "CULTIVATION",
                3L,
                "yesterday",
                new HarvestCompletedPayload("광주", BigDecimal.TEN));

        assertThatThrownBy(() -> cultivationProcessor.processHarvestCompleted(envelope))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @Test
    void 수확_Envelope에_targetId가_없으면_예외를_던진다() {
        NotificationEnvelope<HarvestCompletedPayload> envelope = new NotificationEnvelope<>(
                "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
                "HARVEST_COMPLETED",
                "cultivation-server",
                "CULTIVATION",
                null,
                "2026-08-27T14:15:30+09:00",
                new HarvestCompletedPayload("광주", BigDecimal.TEN));

        assertThatThrownBy(() -> cultivationProcessor.processHarvestCompleted(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetId");
    }

    @Test
    void 멤버추가_Envelope에_targetId가_없으면_예외를_던진다() {
        NotificationEnvelope<MemberAddedPayload> envelope = new NotificationEnvelope<>(
                "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
                "MEMBER_ADDED",
                "cultivation-server",
                "USER",
                null,
                "2026-08-27T14:15:30+09:00",
                new MemberAddedPayload(3L, "광주", "MEMBER"));

        assertThatThrownBy(() -> cultivationProcessor.processMemberAdded(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetId");
    }

    @Test
    void 멤버추가_Envelope는_USER_targetId를_추가된_사용자로_변환한다() {
        NotificationEnvelope<MemberAddedPayload> envelope = new NotificationEnvelope<>(
                "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487",
                "MEMBER_ADDED",
                "cultivation-server",
                "USER",
                21L,
                "2026-08-27T14:15:30+09:00",
                new MemberAddedPayload(3L, "광주", "MEMBER"));

        RabbitMqNotificationCommand command = cultivationProcessor.processMemberAdded(envelope);

        assertThat(command.eventCode()).isEqualTo("MEMBER_ADDED");
        assertThat(command.targetType()).isEqualTo("USER");
        assertThat(command.targetId()).isEqualTo(21L);
        assertThat(command.payload()).isEqualTo(Map.of(
                "cultivationId", 3L,
                "cultivationName", "광주",
                "role", "MEMBER"));
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
    void 수확량이_없으면_수확완료_이벤트_계약_예외를_던진다() {
        CultivationEvent.HarvestCompletedEvent event = new CultivationEvent.HarvestCompletedEvent(
                UUID.randomUUID(), 2L, 7L, "토마토 A동", 8L, null, OCCURRED_AT);

        assertThatThrownBy(() -> cultivationProcessor.process(event))
                .isInstanceOf(site.yesaido.notification_server.rabbitmq.exception.RabbitMqHarvestQuantityMissingException.class);
    }

    @Test
    void 문의_수신자가_없으면_문의이벤트_계약_예외를_던진다() {
        UserEvent.InquirySubmittedEvent event = new UserEvent.InquirySubmittedEvent(
                UUID.randomUUID(), 2L, java.util.List.of(), 9L,
                "문의 제목", "GENERAL", "url", UserEvent.InquiryType.ANSWER, OCCURRED_AT);

        assertThatThrownBy(() -> userProcessor.process(event))
                .isInstanceOf(site.yesaido.notification_server.rabbitmq.exception.RabbitMqInquiryRecipientMissingException.class);
    }

    @Test
    void 로그인과_비밀번호_변경_실패도_실패용_이벤트로_변환한다() {
        RabbitMqNotificationCommand login = userProcessor.process(
                new UserEvent.UserLoginAttemptedEvent(UUID.randomUUID(), 2L, "tester", false, "Seoul", OCCURRED_AT));
        RabbitMqNotificationCommand password = userProcessor.process(
                new UserEvent.UserPasswordChangeAttemptedEvent(
                        UUID.randomUUID(), 2L, "tester", false, OCCURRED_AT));

        assertThat(login.eventCode()).isEqualTo("LOGIN_FAILED");
        assertThat(password.eventCode()).isEqualTo("PASSWORD_CHANGE_FAILED");
    }

    @Test
    void 비밀번호와_재활성화_닉네임이_없으면_미제공으로_대체한다() {
        RabbitMqNotificationCommand password = userProcessor.process(
                new UserEvent.UserPasswordChangeAttemptedEvent(
                        UUID.randomUUID(), 2L, null, true, OCCURRED_AT));
        RabbitMqNotificationCommand reactivation = userProcessor.process(
                new UserEvent.UserAccountReactivationAttemptedEvent(
                        UUID.randomUUID(), 2L, "", false, OCCURRED_AT));

        assertThat(password.payload()).isEqualTo(Map.of("nickname", "미제공"));
        assertThat(reactivation.payload()).isEqualTo(Map.of("nickname", "미제공"));
    }

    @Test
    void 센서오프라인과_멤버초대의_누락된_문자열은_미제공으로_대체한다() {
        RabbitMqNotificationCommand offline = cultivationProcessor.process(
                new CultivationEvent.SensorDataUnavailableEvent(
                        UUID.randomUUID(), 7L, null, "센서 오프라인", OCCURRED_AT));
        RabbitMqNotificationCommand invited = cultivationProcessor.process(
                new CultivationEvent.CultivationMemberInvitedEvent(
                        UUID.randomUUID(), 7L, 1L, null, 2L, "", null, OCCURRED_AT));

        assertThat(offline.payload()).isEqualTo(Map.of(
                "cultivationName", "미제공",
                "deviceName", "미제공"));
        assertThat(invited.payload()).isEqualTo(Map.of(
                "inviterNickname", "미제공",
                "inviteeNickname", "미제공",
                "invitationUrl", "미제공"));
    }

    @Test
    void 일일피드백은_요약과_공개URL을_넣고_문의와_로그인은_기존변수를_유지한다() {
        RabbitMqNotificationCommand feedback = aiProcessor.process(
                new AiEvent.DailyFeedbackGeneratedEvent(
                        UUID.randomUUID(), 2L, 7L, "토마토 A동",
                        "/cultivations/10/daily-feedbacks/2026-08-31", "성장 중", OCCURRED_AT));
        RabbitMqNotificationCommand inquiry = userProcessor.process(
                new UserEvent.InquirySubmittedEvent(UUID.randomUUID(), 2L, java.util.List.of(1L), 9L,
                        "문의 제목", "GENERAL", "url", UserEvent.InquiryType.ANSWER, OCCURRED_AT));
        RabbitMqNotificationCommand login = userProcessor.process(
                new UserEvent.UserLoginAttemptedEvent(UUID.randomUUID(), 2L, "tester", true, "Seoul", OCCURRED_AT));

        assertThat(feedback.payload()).isEqualTo(Map.of(
                "cultivationName", "토마토 A동",
                "feedbackSummary", "성장 중",
                "feedbackUrl", "https://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31"));
        assertThat(inquiry.payload()).isEqualTo(Map.of("inquiryTitle", "문의 제목"));
        assertThat(inquiry.recipientUserIds()).containsExactly(1L);
        assertThat(login.payload()).isEqualTo(Map.of("provider", "미제공"));
    }

    @Test
    void 일일피드백_URL은_상대경로에_origin을_붙이고_공백은_미제공_절대경로는_유지한다() {
        RabbitMqNotificationCommand withUrl = aiProcessor.process(
                new AiEvent.DailyFeedbackGeneratedEvent(
                        UUID.randomUUID(), 2L, 7L, "토마토 A동",
                        "/cultivations/10/daily-feedbacks/2026-08-31", "성장 중", OCCURRED_AT));
        RabbitMqNotificationCommand withoutUrl = aiProcessor.process(
                new AiEvent.DailyFeedbackGeneratedEvent(
                        UUID.randomUUID(), 2L, 7L, "토마토 A동", " ", "성장 중", OCCURRED_AT));
        RabbitMqNotificationCommand absolute = aiProcessor.process(
                new AiEvent.DailyFeedbackGeneratedEvent(
                        UUID.randomUUID(), 2L, 7L, "토마토 A동",
                        "https://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31",
                        "성장 중", OCCURRED_AT));
        RabbitMqNotificationCommand mixedCase = aiProcessor.process(
                new AiEvent.DailyFeedbackGeneratedEvent(
                        UUID.randomUUID(), 2L, 7L, "토마토 A동",
                        "HTTPS://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31",
                        "성장 중", OCCURRED_AT));
        AiNotificationProcessor localOrigin = new AiNotificationProcessor("http://localhost:8080/");
        RabbitMqNotificationCommand local = localOrigin.process(
                new AiEvent.DailyFeedbackGeneratedEvent(
                        UUID.randomUUID(), 2L, 7L, "토마토 A동",
                        "/cultivations/10/daily-feedbacks/2026-08-31", "성장 중", OCCURRED_AT));

        assertThat((Map<String, Object>) withUrl.payload())
                .containsEntry("feedbackUrl", "https://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31");
        assertThat((Map<String, Object>) withoutUrl.payload())
                .containsEntry("feedbackUrl", "미제공");
        assertThat((Map<String, Object>) absolute.payload())
                .containsEntry("feedbackUrl", "https://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31");
        assertThat((Map<String, Object>) mixedCase.payload())
                .containsEntry("feedbackUrl", "HTTPS://yes-nhn.site/cultivations/10/daily-feedbacks/2026-08-31");
        assertThat((Map<String, Object>) local.payload())
                .containsEntry("feedbackUrl", "http://localhost:8080/cultivations/10/daily-feedbacks/2026-08-31");
    }
}
