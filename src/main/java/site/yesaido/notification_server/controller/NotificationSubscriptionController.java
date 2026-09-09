package site.yesaido.notification_server.controller;

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
import site.yesaido.notification_server.controller.docs.NotificationSubscriptionControllerDocs;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionEnabledRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.service.NotificationSubscriptionService;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-subscriptions")
public class NotificationSubscriptionController implements NotificationSubscriptionControllerDocs {

    private final NotificationSubscriptionService subscriptionService;

    @Override    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(
            @RequestHeader("X-User-Id")
            Long userId,
            @RequestBody SubscriptionCreateRequest request
    ) {
        SubscriptionResponse response = subscriptionService.create(userId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/notification-subscriptions/" + response.id())).body(response);
    }

    @Override    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> findAll(
            @RequestHeader("X-User-Id")
            Long userId
    ) {
        return ResponseEntity.ok(subscriptionService.findAll(userId));
    }

    @Override    @PatchMapping("/{subscriptionId}/enabled")
    public ResponseEntity<SubscriptionResponse> changeEnabled(
            @RequestHeader("X-User-Id")
            Long userId,
            @PathVariable
            Long subscriptionId,
            @RequestBody SubscriptionEnabledRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.changeEnabled(
                userId, subscriptionId, request.enabled()));
    }

    @Override    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id")
            Long userId,
            @PathVariable
            Long subscriptionId
    ) {
        subscriptionService.delete(userId, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
