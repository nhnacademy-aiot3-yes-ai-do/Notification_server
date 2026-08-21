package site.yesaido.notification_server.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
import site.yesaido.notification_server.validation.ValidationMessages;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-subscriptions")
public class NotificationSubscriptionController {

    private final NotificationSubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Valid @RequestBody SubscriptionCreateRequest request
    ) {
        SubscriptionResponse response = subscriptionService.create(userId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/notification-subscriptions/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> findAll(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId
    ) {
        return ResponseEntity.ok(subscriptionService.findAll(userId));
    }

    @PatchMapping("/{subscriptionId}/enabled")
    public ResponseEntity<SubscriptionResponse> changeEnabled(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @PathVariable
            @Positive(message = ValidationMessages.SUBSCRIPTION_ID_POSITIVE) Long subscriptionId,
            @Valid @RequestBody SubscriptionEnabledRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.changeEnabled(
                userId, subscriptionId, request.enabled()));
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @PathVariable
            @Positive(message = ValidationMessages.SUBSCRIPTION_ID_POSITIVE) Long subscriptionId
    ) {
        subscriptionService.delete(userId, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
