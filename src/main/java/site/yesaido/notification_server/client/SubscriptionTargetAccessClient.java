package site.yesaido.notification_server.client;

import java.time.Duration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import site.yesaido.notification_server.config.property.SubscriptionAccessProperties;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessDeniedException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessUnverifiedException;

@Component
public class SubscriptionTargetAccessClient {

    private static final ParameterizedTypeReference<UserApiResponse<InquiryAccessResponse>> INQUIRY_ACCESS_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient cultivationClient;
    private final RestClient userClient;

    public SubscriptionTargetAccessClient(SubscriptionAccessProperties properties) {
        this.cultivationClient = restClient(properties.cultivationUrl(), properties);
        this.userClient = restClient(properties.userUrl(), properties);
    }

    SubscriptionTargetAccessClient(RestClient cultivationClient, RestClient userClient) {
        this.cultivationClient = cultivationClient;
        this.userClient = userClient;
    }

    public void requireCultivationAccess(Long userId, Long cultivationId) {
        try {
            cultivationClient.get()
                    .uri("/api/v1/cultivations/{id}", cultivationId)
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .onStatus(this::isDenied, (request, response) -> {
                        throw new SubscriptionTargetAccessDeniedException(
                                "cultivation id:%d".formatted(cultivationId));
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new SubscriptionTargetAccessUnverifiedException(
                                "cultivation id:%d status:%d".formatted(cultivationId, response.getStatusCode().value()));
                    })
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new SubscriptionTargetAccessUnverifiedException(
                    "cultivation id:%d".formatted(cultivationId), exception);
        }
    }

    public void requireInquiryAccess(Long userId, Long inquiryId) {
        UserApiResponse<InquiryAccessResponse> body;
        try {
            body = userClient.get()
                    .uri("/api/v1/inquiries/{id}/access", inquiryId)
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new SubscriptionTargetAccessUnverifiedException(
                                "inquiry id:%d status:%d".formatted(inquiryId, response.getStatusCode().value()));
                    })
                    .body(INQUIRY_ACCESS_TYPE);
        } catch (RestClientException exception) {
            throw new SubscriptionTargetAccessUnverifiedException(
                    "inquiry id:%d".formatted(inquiryId), exception);
        }
        if (body == null || body.data() == null
                || body.success() == null || body.data().allowed() == null) {
            throw new SubscriptionTargetAccessUnverifiedException("inquiry id:%d empty body".formatted(inquiryId));
        }
        if (!body.success() || !body.data().allowed()) {
            throw new SubscriptionTargetAccessDeniedException("inquiry id:%d".formatted(inquiryId));
        }
    }

    private boolean isDenied(HttpStatusCode status) {
        return status.value() == 403 || status.value() == 404;
    }

    private static RestClient restClient(String baseUrl, SubscriptionAccessProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(durationMillis(properties.connectTimeout(), 2_000));
        factory.setReadTimeout(durationMillis(properties.readTimeout(), 3_000));
        return RestClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl)
                .requestFactory(factory)
                .build();
    }

    private static int durationMillis(Duration duration, int fallback) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return fallback;
        }
        return Math.toIntExact(duration.toMillis());
    }
}
