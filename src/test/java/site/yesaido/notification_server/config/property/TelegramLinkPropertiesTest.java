package site.yesaido.notification_server.config.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TelegramLinkPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TelegramLinkPropertiesConfiguration.class)
            .withPropertyValues(
                    "notification.telegram-link.bot-username=yes_ai_do_farm_alert_bot",
                    "notification.telegram-link.expiration=PT10M");

    @Test
    void rejectsBlankWebhookSecretAtStartup() {
        contextRunner.withPropertyValues("notification.telegram-link.webhook-secret= ")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void bindsValidWebhookConfiguration() {
        contextRunner.withPropertyValues("notification.telegram-link.webhook-secret=valid_webhook-secret_123")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(TelegramLinkProperties.class).webhookSecret())
                            .isEqualTo("valid_webhook-secret_123");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TelegramLinkProperties.class)
    static class TelegramLinkPropertiesConfiguration {
    }
}
