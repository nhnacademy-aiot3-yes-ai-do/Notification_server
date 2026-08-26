package site.yesaido.notification_server.config.property;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.recovery")
public record NotificationRecoveryProperties(
        Duration pendingDeliveryMinAge,
        Duration sendingClaimTimeout,
        int pendingDeliveryBatchSize
) {
}
