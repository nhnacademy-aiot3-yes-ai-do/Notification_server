package site.yesaido.notification_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import site.yesaido.notification_server.config.SubscriptionAccessFeignConfig;

@FeignClient(
        name = "userInquiryAccessClient",
        url = "${notification.access.user-url}",
        configuration = SubscriptionAccessFeignConfig.class
)
public interface UserInquiryAccessFeignClient {

    @GetMapping("/api/v1/inquiries/{id}/access")
    UserApiResponse<InquiryAccessResponse> getInquiryAccess(
            @PathVariable("id") Long inquiryId,
            @RequestHeader("X-User-Id") String userId
    );
}
