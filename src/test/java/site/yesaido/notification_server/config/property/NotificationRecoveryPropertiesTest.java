package site.yesaido.notification_server.config.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class NotificationRecoveryPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationRecoveryPropertiesConfiguration.class)
            .withPropertyValues(
                    "notification.recovery.pending-delivery-min-age=PT30S",
                    "notification.recovery.sending-claim-timeout=PT5M",
                    "notification.recovery.pending-delivery-batch-size=100");

    @Test
    void rejectsNonPositiveRecoveryDurationsAtStartup() {
        contextRunner.withPropertyValues("notification.recovery.pending-delivery-min-age=PT0S")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
        contextRunner.withPropertyValues("notification.recovery.sending-claim-timeout=PT-1S")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void rejectsNonPositiveRecoveryBatchSizeAtStartup() {
        contextRunner.withPropertyValues("notification.recovery.pending-delivery-batch-size=0")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
        contextRunner.withPropertyValues("notification.recovery.pending-delivery-batch-size=-1")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationRecoveryProperties.class)
    static class NotificationRecoveryPropertiesConfiguration {
    }
}
