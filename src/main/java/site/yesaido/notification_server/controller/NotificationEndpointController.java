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
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointEnabledRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;
import site.yesaido.notification_server.service.NotificationEndpointService;

@Validated
@RestController
@RequestMapping("/api/v1/notification-endpoints")
public class NotificationEndpointController {

    private final NotificationEndpointService endpointService;

    public NotificationEndpointController(NotificationEndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    public ResponseEntity<EndpointResponse> create(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Valid @RequestBody EndpointCreateRequest request
    ) {
        EndpointResponse response = endpointService.create(userId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/notification-endpoints/" + response.id())).body(response);
    }

    @GetMapping
    public List<EndpointResponse> findAll(
            @RequestHeader("X-User-Id") @Positive Long userId
    ) {
        return endpointService.findAll(userId);
    }

    @PatchMapping("/{endpointId}")
    public EndpointResponse update(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long endpointId,
            @Valid @RequestBody EndpointUpdateRequest request
    ) {
        return endpointService.update(userId, endpointId, request);
    }

    @PatchMapping("/{endpointId}/enabled")
    public EndpointResponse changeEnabled(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long endpointId,
            @Valid @RequestBody EndpointEnabledRequest request
    ) {
        return endpointService.changeEnabled(userId, endpointId, request.enabled());
    }

    @DeleteMapping("/{endpointId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long endpointId
    ) {
        endpointService.delete(userId, endpointId);
        return ResponseEntity.noContent().build();
    }
}
