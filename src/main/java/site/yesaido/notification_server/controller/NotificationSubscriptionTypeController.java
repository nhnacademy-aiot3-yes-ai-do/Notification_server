package site.yesaido.notification_server.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;
import site.yesaido.notification_server.service.NotificationSubscriptionService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-subscription-types")
public class NotificationSubscriptionTypeController {

    private final NotificationSubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<SubscriptionTypeResponse>> findAll() {
        return ResponseEntity.ok(subscriptionService.findTypes());
    }
}
