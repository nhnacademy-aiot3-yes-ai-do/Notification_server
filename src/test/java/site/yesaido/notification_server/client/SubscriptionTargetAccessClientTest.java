package site.yesaido.notification_server.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessDeniedException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessUnverifiedException;

class SubscriptionTargetAccessClientTest {

    private MockRestServiceServer cultivationServer;
    private MockRestServiceServer userServer;
    private SubscriptionTargetAccessClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder cultivationBuilder = RestClient.builder().baseUrl("http://cultivation.test");
        RestClient.Builder userBuilder = RestClient.builder().baseUrl("http://user.test");
        cultivationServer = MockRestServiceServer.bindTo(cultivationBuilder).build();
        userServer = MockRestServiceServer.bindTo(userBuilder).build();
        client = new SubscriptionTargetAccessClient(cultivationBuilder.build(), userBuilder.build());
    }

    @Test
    void 재배지_200이면_구독을_허용한다() {
        cultivationServer.expect(requestTo("http://cultivation.test/api/v1/cultivations/4"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "20"))
                .andRespond(withSuccess("{\"id\":4}", MediaType.APPLICATION_JSON));

        client.requireCultivationAccess(20L, 4L);
        cultivationServer.verify();
    }

    @Test
    void 재배지_403이면_거절한다() {
        cultivationServer.expect(requestTo("http://cultivation.test/api/v1/cultivations/4"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.requireCultivationAccess(20L, 4L))
                .isInstanceOf(SubscriptionTargetAccessDeniedException.class);
    }

    @Test
    void 재배지_404이면_거절한다() {
        cultivationServer.expect(requestTo("http://cultivation.test/api/v1/cultivations/4"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.requireCultivationAccess(20L, 4L))
                .isInstanceOf(SubscriptionTargetAccessDeniedException.class);
    }

    @Test
    void 재배지_5xx이면_확인실패로_막는다() {
        cultivationServer.expect(requestTo("http://cultivation.test/api/v1/cultivations/4"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.requireCultivationAccess(20L, 4L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    @Test
    void 문의_allowed_true면_허용한다() {
        userServer.expect(requestTo("http://user.test/api/v1/inquiries/55/access"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "7"))
                .andRespond(withSuccess(
                        "{\"success\":true,\"message\":\"ok\",\"data\":{\"allowed\":true}}",
                        MediaType.APPLICATION_JSON));

        client.requireInquiryAccess(7L, 55L);
        userServer.verify();
    }

    @Test
    void 문의_allowed_false면_거절한다() {
        userServer.expect(requestTo("http://user.test/api/v1/inquiries/55/access"))
                .andRespond(withSuccess(
                        "{\"success\":true,\"message\":\"ok\",\"data\":{\"allowed\":false}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessDeniedException.class);
    }

    @Test
    void 문의_응답필드가_없으면_확인실패로_막는다() {
        userServer.expect(requestTo("http://user.test/api/v1/inquiries/55/access"))
                .andRespond(withSuccess(
                        "{\"message\":\"ok\",\"data\":{}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    @Test
    void 문의_5xx이면_확인실패로_막는다() {
        userServer.expect(requestTo("http://user.test/api/v1/inquiries/55/access"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }
}
