package site.yesaido.notification_server.config;

import feign.Request;
import feign.Retryer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import site.yesaido.notification_server.config.property.SubscriptionAccessProperties;

public class SubscriptionAccessFeignConfig {

    @Bean
    public Request.Options subscriptionAccessFeignOptions(SubscriptionAccessProperties properties) {
        return new Request.Options(
                durationMillis(properties.connectTimeout(), 2_000), TimeUnit.MILLISECONDS,
                durationMillis(properties.readTimeout(), 3_000), TimeUnit.MILLISECONDS,
                true);
    }

    @Bean
    public Retryer subscriptionAccessFeignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    static long durationMillis(Duration duration, long fallback) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return fallback;
        }
        return duration.toMillis();
    }
}
