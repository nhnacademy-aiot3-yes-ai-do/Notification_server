package site.yesaido.notification_server.client;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessDeniedException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessUnverifiedException;

@Component
@RequiredArgsConstructor
public class SubscriptionTargetAccessClient {

    private final CultivationAccessFeignClient cultivationAccessClient;
    private final UserInquiryAccessFeignClient userInquiryAccessClient;

    public void requireCultivationAccess(Long userId, Long cultivationId) {
        try {
            cultivationAccessClient.getCultivation(cultivationId, String.valueOf(userId));
        } catch (RetryableException exception) {
            throw new SubscriptionTargetAccessUnverifiedException(
                    "cultivation id:%d".formatted(cultivationId), exception);
        } catch (FeignException exception) {
            if (isDenied(exception.status())) {
                throw new SubscriptionTargetAccessDeniedException(
                        "cultivation id:%d".formatted(cultivationId));
            }
            throw new SubscriptionTargetAccessUnverifiedException(
                    "cultivation id:%d status:%d".formatted(cultivationId, exception.status()), exception);
        }
    }

    public void requireInquiryAccess(Long userId, Long inquiryId) {
        UserApiResponse<InquiryAccessResponse> body;
        try {
            body = userInquiryAccessClient.getInquiryAccess(inquiryId, String.valueOf(userId));
        } catch (RetryableException exception) {
            throw new SubscriptionTargetAccessUnverifiedException(
                    "inquiry id:%d".formatted(inquiryId), exception);
        } catch (FeignException exception) {
            throw new SubscriptionTargetAccessUnverifiedException(
                    "inquiry id:%d status:%d".formatted(inquiryId, exception.status()), exception);
        }
        if (body == null || body.data() == null
                || body.success() == null || body.data().allowed() == null) {
            throw new SubscriptionTargetAccessUnverifiedException("inquiry id:%d empty body".formatted(inquiryId));
        }
        if (!Boolean.TRUE.equals(body.success()) || !Boolean.TRUE.equals(body.data().allowed())) {
            throw new SubscriptionTargetAccessDeniedException("inquiry id:%d".formatted(inquiryId));
        }
    }

    private boolean isDenied(int status) {
        return status == 403 || status == 404;
    }
}
