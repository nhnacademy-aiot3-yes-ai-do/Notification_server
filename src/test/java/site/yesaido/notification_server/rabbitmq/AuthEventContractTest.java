package site.yesaido.notification_server.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;

class AuthEventContractTest {

    @Test
    void login_type_id_header의_json은_login_event로_역직렬화된다() {
        AuthRabbitListenerConfig config = new AuthRabbitListenerConfig();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(AuthEventContract.CONTENT_TYPE);
        properties.setHeader(AuthEventContract.TYPE_ID_HEADER, AuthEventContract.LOGIN_ATTEMPTED_TYPE_ID);
        Message message = new Message(("""
                {"userId":1,"nickname":"tester","succeeded":true,
                 "loginLocation":"Seoul","occurredAt":"2026-08-11T12:00:00+09:00"}
                """).getBytes(StandardCharsets.UTF_8), properties);

        Object event = config.authEventMessageConverter().fromMessage(message);

        assertThat(event).isInstanceOf(UserEvent.UserLoginAttemptedEvent.class);
        assertThat((UserEvent.UserLoginAttemptedEvent) event).extracting(UserEvent.UserLoginAttemptedEvent::nickname)
                .isEqualTo("tester");
    }
}
