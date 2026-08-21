package site.yesaido.notification_server.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import site.yesaido.notification_server.dto.delivery.DeliveryPageResponse;
import site.yesaido.notification_server.dto.delivery.DeliveryResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;
import site.yesaido.notification_server.entity.DeliveryStatus;
import site.yesaido.notification_server.entity.NotificationDelivery;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.entity.NotificationSubscriptionType;

class NotificationResponseMappingTest {

    @Test
    void Delivery를_API_응답필드로_변환한다() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 20, 10, 0);
        NotificationDelivery delivery = delivery(7L, createdAt);

        DeliveryResponse response = DeliveryResponse.from(delivery);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.notificationId()).isEqualTo(17L);
        assertThat(response.subscriptionId()).isEqualTo(27L);
        assertThat(response.channelCode()).isEqualTo("DISCORD");
        assertThat(response.message()).isEqualTo("온도가 임계값을 초과했습니다.");
        assertThat(response.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(response.attemptCount()).isEqualTo((short) 1);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void Delivery_Page를_내부_Spring타입이_아닌_페이지응답으로_변환한다() {
        PageImpl<NotificationDelivery> page = new PageImpl<>(
                List.of(delivery(7L, LocalDateTime.now())), PageRequest.of(0, 1), 2);

        DeliveryPageResponse response = DeliveryPageResponse.from(page);

        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void 구독종류를_이벤트와_대상정보까지_포함해_변환한다() {
        NotificationSubscriptionType type = mock(
                NotificationSubscriptionType.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(type.getId()).thenReturn(5L);
        when(type.getName()).thenReturn("임계값 초과");
        when(type.getDescription()).thenReturn("환경 임계값 초과 알림");
        when(type.getEventType().getCode()).thenReturn("ENVIRONMENT_THRESHOLD_BREACHED");
        when(type.getTargetType().getTargetType()).thenReturn("CULTIVATION");

        SubscriptionTypeResponse response = SubscriptionTypeResponse.from(type);

        assertThat(response).isEqualTo(new SubscriptionTypeResponse(
                5L, "임계값 초과", "환경 임계값 초과 알림",
                "ENVIRONMENT_THRESHOLD_BREACHED", "CULTIVATION"));
    }

    @Test
    void Telegram_목적지는_마지막_네자리만_노출한다() {
        assertThat(EndpointResponse.from(endpoint("TELEGRAM", "123456789")).destination())
                .isEqualTo("*****6789");
        assertThat(EndpointResponse.from(endpoint("TELEGRAM", "123")).destination())
                .isEqualTo("***123");
    }

    @Test
    void Discord_목적지는_마지막_토큰을_마스킹한다() {
        assertThat(EndpointResponse.from(endpoint(
                "DISCORD", "https://discord.com/api/webhooks/1/secret-token")).destination())
                .isEqualTo("https://discord.com/api/webhooks/1/***");
        assertThat(EndpointResponse.from(endpoint("DISCORD", "invalid-without-slash")).destination())
                .isEqualTo("***");
    }

    @Test
    void 비어있거나_알수없는_채널의_목적지는_전부_숨긴다() {
        assertThat(EndpointResponse.from(endpoint("EMAIL", null)).destination()).isEqualTo("***");
        assertThat(EndpointResponse.from(endpoint("EMAIL", "   ")).destination()).isEqualTo("***");
        assertThat(EndpointResponse.from(endpoint("EMAIL", "user@example.com")).destination())
                .isEqualTo("***");
    }

    private NotificationDelivery delivery(Long id, LocalDateTime createdAt) {
        NotificationDelivery delivery = mock(
                NotificationDelivery.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(delivery.getId()).thenReturn(id);
        when(delivery.getNotification().getId()).thenReturn(17L);
        when(delivery.getSubscription().getId()).thenReturn(27L);
        when(delivery.getSubscription().getEndpoint().getChannelType().getCode()).thenReturn("DISCORD");
        when(delivery.getRenderedMessage()).thenReturn("온도가 임계값을 초과했습니다.");
        when(delivery.getStatus()).thenReturn(DeliveryStatus.PENDING);
        when(delivery.getAttemptCount()).thenReturn((short) 1);
        when(delivery.getCreatedAt()).thenReturn(createdAt);
        return delivery;
    }

    private NotificationEndpoint endpoint(String channelCode, String destination) {
        NotificationEndpoint endpoint = mock(
                NotificationEndpoint.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(endpoint.getId()).thenReturn(1L);
        when(endpoint.getChannelType().getId()).thenReturn(2L);
        when(endpoint.getChannelType().getCode()).thenReturn(channelCode);
        when(endpoint.getChannelType().getDisplayName()).thenReturn(channelCode);
        when(endpoint.getDestination()).thenReturn(destination);
        when(endpoint.getDisplayName()).thenReturn("알림 채널");
        when(endpoint.isEnabled()).thenReturn(true);
        return endpoint;
    }
}
