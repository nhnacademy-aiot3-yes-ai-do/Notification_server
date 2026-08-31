package site.yesaido.notification_server.config;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Retryer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.config.property.SubscriptionAccessProperties;

class SubscriptionAccessFeignConfigTest {

    @Test
    void 내부호출_타임아웃과_재시도없음을_설정한다() {
        SubscriptionAccessFeignConfig config = new SubscriptionAccessFeignConfig();
        SubscriptionAccessProperties properties = new SubscriptionAccessProperties(
                "http://cultivation.test",
                "http://user.test",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));

        Request.Options options = config.subscriptionAccessFeignOptions(properties);

        assertThat(options.connectTimeoutMillis()).isEqualTo(2_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3_000);
        assertThat(config.subscriptionAccessFeignRetryer()).isEqualTo(Retryer.NEVER_RETRY);
    }

    @Test
    void 타임아웃이_null_0_음수면_connect_2초_read_3초로_정규화한다() {
        assertNormalized(null, null);
        assertNormalized(Duration.ZERO, Duration.ZERO);
        assertNormalized(Duration.ofSeconds(-1), Duration.ofMillis(-5));
    }

    private static void assertNormalized(Duration connectTimeout, Duration readTimeout) {
        SubscriptionAccessProperties properties = new SubscriptionAccessProperties(
                "http://cultivation.test",
                "http://user.test",
                connectTimeout,
                readTimeout);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));

        Request.Options options = new SubscriptionAccessFeignConfig().subscriptionAccessFeignOptions(properties);
        assertThat(options.connectTimeoutMillis()).isEqualTo(2_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3_000);

        assertThat(SubscriptionAccessFeignConfig.durationMillis(connectTimeout, 2_000)).isEqualTo(2_000);
        assertThat(SubscriptionAccessFeignConfig.durationMillis(readTimeout, 3_000)).isEqualTo(3_000);
    }
}
