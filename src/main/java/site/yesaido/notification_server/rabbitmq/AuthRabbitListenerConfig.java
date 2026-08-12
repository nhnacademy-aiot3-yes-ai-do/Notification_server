package site.yesaido.notification_server.rabbitmq;

import static site.yesaido.notification_server.rabbitmq.AuthEventContract.ACCOUNT_REACTIVATION_ATTEMPTED_TYPE_ID;
import static site.yesaido.notification_server.rabbitmq.AuthEventContract.LOGIN_ATTEMPTED_TYPE_ID;
import static site.yesaido.notification_server.rabbitmq.AuthEventContract.PASSWORD_CHANGE_ATTEMPTED_TYPE_ID;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import java.util.Map;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;

@Configuration
public class AuthRabbitListenerConfig {

    @Bean
    public Jackson2JsonMessageConverter authEventMessageConverter() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("site.yesaido.notification_server.rabbitmq.event");
        typeMapper.setIdClassMapping(Map.of(
                LOGIN_ATTEMPTED_TYPE_ID, UserEvent.UserLoginAttemptedEvent.class,
                PASSWORD_CHANGE_ATTEMPTED_TYPE_ID, UserEvent.UserPasswordChangeAttemptedEvent.class,
                ACCOUNT_REACTIVATION_ATTEMPTED_TYPE_ID, UserEvent.UserAccountReactivationAttemptedEvent.class
        ));

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory authRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter authEventMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setMessageConverter(authEventMessageConverter);
        return factory;
    }
}
