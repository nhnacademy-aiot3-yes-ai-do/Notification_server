package site.yesaido.notification_server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import site.yesaido.notification_server.config.property.NotificationProperties;
import site.yesaido.notification_server.config.property.NotificationRecoveryProperties;
import site.yesaido.notification_server.config.property.SubscriptionAccessProperties;
import site.yesaido.notification_server.config.property.TelegramLinkProperties;

class NotificationServiceApplicationTest {

    @Test
    void NotificationProperties를_명시적으로_ConfigurationProperties_Bean으로_등록한다() {
        EnableConfigurationProperties annotation = NotificationServiceApplication.class
                .getAnnotation(EnableConfigurationProperties.class);

        assertThat(annotation).isNotNull();
        assertThat(Arrays.asList(annotation.value())).containsExactlyInAnyOrder(
                NotificationProperties.class,
                NotificationRecoveryProperties.class,
                TelegramLinkProperties.class,
                SubscriptionAccessProperties.class);
    }
}
