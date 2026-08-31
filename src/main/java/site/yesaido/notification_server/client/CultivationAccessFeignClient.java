package site.yesaido.notification_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import site.yesaido.notification_server.config.SubscriptionAccessFeignConfig;

@FeignClient(
        name = "cultivationAccessClient",
        url = "${notification.access.cultivation-url}",
        configuration = SubscriptionAccessFeignConfig.class
)
public interface CultivationAccessFeignClient {

    @GetMapping("/api/v1/cultivations/{id}")
    void getCultivation(
            @PathVariable("id") Long cultivationId,
            @RequestHeader("X-User-Id") String userId
    );
}
