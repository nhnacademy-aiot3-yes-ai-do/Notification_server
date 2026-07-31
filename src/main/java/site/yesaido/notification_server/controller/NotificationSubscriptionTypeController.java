package site.yesaido.notification_server.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;
import site.yesaido.notification_server.service.NotificationSubscriptionService;

@RestController
@RequestMapping("/api/v1/notification-subscription-types")
public class NotificationSubscriptionTypeController {

    private final NotificationSubscriptionService subscriptionService;

    public NotificationSubscriptionTypeController(
            NotificationSubscriptionService subscriptionService
    ) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public List<SubscriptionTypeResponse> findAll() {
        return subscriptionService.findTypes();
    }
}
