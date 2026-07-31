package site.yesaido.notification_server.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionEnabledRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.service.NotificationSubscriptionService;

@Validated
@RestController
@RequestMapping("/api/v1/notification-subscriptions")
public class NotificationSubscriptionController {

    private final NotificationSubscriptionService subscriptionService;

    public NotificationSubscriptionController(
            NotificationSubscriptionService subscriptionService
    ) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Valid @RequestBody SubscriptionCreateRequest request
    ) {
        SubscriptionResponse response = subscriptionService.create(userId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/notification-subscriptions/" + response.id())).body(response);
    }

    @GetMapping
    public List<SubscriptionResponse> findAll(
            @RequestHeader("X-User-Id") @Positive Long userId
    ) {
        return subscriptionService.findAll(userId);
    }

    @PatchMapping("/{subscriptionId}/enabled")
    public SubscriptionResponse changeEnabled(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long subscriptionId,
            @Valid @RequestBody SubscriptionEnabledRequest request
    ) {
        return subscriptionService.changeEnabled(
                userId, subscriptionId, request.enabled());
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long subscriptionId
    ) {
        subscriptionService.delete(userId, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
