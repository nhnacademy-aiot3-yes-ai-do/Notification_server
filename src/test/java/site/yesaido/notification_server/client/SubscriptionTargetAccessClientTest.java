package site.yesaido.notification_server.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessDeniedException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessUnverifiedException;

class SubscriptionTargetAccessClientTest {

    private CultivationAccessFeignClient cultivationAccessClient;
    private UserInquiryAccessFeignClient userInquiryAccessClient;
    private SubscriptionTargetAccessClient client;

    @BeforeEach
    void setUp() {
        cultivationAccessClient = mock(CultivationAccessFeignClient.class);
        userInquiryAccessClient = mock(UserInquiryAccessFeignClient.class);
        client = new SubscriptionTargetAccessClient(cultivationAccessClient, userInquiryAccessClient);
    }

    @Test
    void 재배지_200이면_구독을_허용한다() {
        assertThatCode(() -> client.requireCultivationAccess(20L, 4L)).doesNotThrowAnyException();
        verify(cultivationAccessClient).getCultivation(4L, "20");
    }

    @Test
    void 재배지_403이면_거절한다() {
        doThrow(feignException(403)).when(cultivationAccessClient).getCultivation(4L, "20");

        assertThatThrownBy(() -> client.requireCultivationAccess(20L, 4L))
                .isInstanceOf(SubscriptionTargetAccessDeniedException.class);
    }

    @Test
    void 재배지_404이면_거절한다() {
        doThrow(feignException(404)).when(cultivationAccessClient).getCultivation(4L, "20");

        assertThatThrownBy(() -> client.requireCultivationAccess(20L, 4L))
                .isInstanceOf(SubscriptionTargetAccessDeniedException.class);
    }

    @Test
    void 재배지_5xx이면_확인실패로_막는다() {
        doThrow(feignException(500)).when(cultivationAccessClient).getCultivation(4L, "20");

        assertThatThrownBy(() -> client.requireCultivationAccess(20L, 4L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    @Test
    void 재배지_타임아웃이면_확인실패로_막는다() {
        doThrow(timeoutException()).when(cultivationAccessClient).getCultivation(4L, "20");

        assertThatThrownBy(() -> client.requireCultivationAccess(20L, 4L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    @Test
    void 문의_allowed_true면_허용한다() {
        when(userInquiryAccessClient.getInquiryAccess(55L, "7"))
                .thenReturn(new UserApiResponse<>(true, "ok", new InquiryAccessResponse(true)));

        assertThatCode(() -> client.requireInquiryAccess(7L, 55L)).doesNotThrowAnyException();
        verify(userInquiryAccessClient).getInquiryAccess(55L, "7");
    }

    @Test
    void 문의_allowed_false면_거절한다() {
        when(userInquiryAccessClient.getInquiryAccess(55L, "7"))
                .thenReturn(new UserApiResponse<>(true, "ok", new InquiryAccessResponse(false)));

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessDeniedException.class);
    }

    @Test
    void 문의_응답필드가_없으면_확인실패로_막는다() {
        when(userInquiryAccessClient.getInquiryAccess(55L, "7"))
                .thenReturn(new UserApiResponse<>(null, "ok", new InquiryAccessResponse(null)));

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    @Test
    void 문의_5xx이면_확인실패로_막는다() {
        when(userInquiryAccessClient.getInquiryAccess(55L, "7")).thenThrow(feignException(500));

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    @Test
    void 문의_403이면_확인실패로_막는다() {
        when(userInquiryAccessClient.getInquiryAccess(55L, "7")).thenThrow(feignException(403));

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    @Test
    void 문의_타임아웃이면_확인실패로_막는다() {
        when(userInquiryAccessClient.getInquiryAccess(55L, "7")).thenThrow(timeoutException());

        assertThatThrownBy(() -> client.requireInquiryAccess(7L, 55L))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
    }

    private static FeignException feignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/api/v1/resource",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Collections.emptyMap())
                .build();
        return FeignException.errorStatus("AccessClient#call()", response);
    }

    private static RetryableException timeoutException() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/api/v1/resource",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return new RetryableException(
                -1,
                "timeout",
                Request.HttpMethod.GET,
                1_000L,
                request
        );
    }
}
