package site.yesaido.notification_server.rabbitmq.refactor.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationEventDefinitionTest {

    @Test
    void 이벤트정의는_DB_eventCode와_구독대상_코드를_함께_가진다() {
        NotificationEventDefinition definition = NotificationEventDefinition.HARVEST_COMPLETED;

        assertThat(definition.code()).isEqualTo("HARVEST_COMPLETED");
        assertThat(definition.targetType()).isEqualTo("CULTIVATION");
    }

    @Test
    void 문의답변은_문의_ID를_구독대상으로_사용한다() {
        NotificationEventDefinition definition = NotificationEventDefinition.INQUIRY_ANSWERED;

        assertThat(definition.code()).isEqualTo("INQUIRY_ANSWERED");
        assertThat(definition.targetType()).isEqualTo("INQUIRY");
    }
}
