package site.yesaido.notification_server.config.property;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.recovery")
public record NotificationRecoveryProperties(
        @NotNull Duration pendingDeliveryMinAge,
        @NotNull Duration sendingClaimTimeout,
        @Positive int pendingDeliveryBatchSize
) {
    @AssertTrue(message = "pendingDeliveryMinAge must be positive")
    boolean hasPositivePendingDeliveryMinAge() {
        return pendingDeliveryMinAge != null && !pendingDeliveryMinAge.isNegative() && !pendingDeliveryMinAge.isZero();
    }

    @AssertTrue(message = "sendingClaimTimeout must be positive")
    boolean hasPositiveSendingClaimTimeout() {
        return sendingClaimTimeout != null && !sendingClaimTimeout.isNegative() && !sendingClaimTimeout.isZero();
    }
}
