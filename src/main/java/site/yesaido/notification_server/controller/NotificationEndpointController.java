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
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointEnabledRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;
import site.yesaido.notification_server.service.NotificationEndpointService;
import site.yesaido.notification_server.validation.ValidationMessages;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-endpoints")
public class NotificationEndpointController {

    private final NotificationEndpointService endpointService;

    @PostMapping
    public ResponseEntity<EndpointResponse> create(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Valid @RequestBody EndpointCreateRequest request
    ) {
        EndpointResponse response = endpointService.create(userId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/notification-endpoints/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EndpointResponse>> findAll(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId
    ) {
        return ResponseEntity.ok(endpointService.findAll(userId));
    }

    @PatchMapping("/{endpointId}")
    public ResponseEntity<EndpointResponse> update(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @PathVariable
            @Positive(message = ValidationMessages.ENDPOINT_ID_POSITIVE) Long endpointId,
            @Valid @RequestBody EndpointUpdateRequest request
    ) {
        return ResponseEntity.ok(endpointService.update(userId, endpointId, request));
    }

    @PatchMapping("/{endpointId}/enabled")
    public ResponseEntity<EndpointResponse> changeEnabled(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @PathVariable
            @Positive(message = ValidationMessages.ENDPOINT_ID_POSITIVE) Long endpointId,
            @Valid @RequestBody EndpointEnabledRequest request
    ) {
        return ResponseEntity.ok(
                endpointService.changeEnabled(userId, endpointId, request.enabled()));
    }

    @DeleteMapping("/{endpointId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @PathVariable
            @Positive(message = ValidationMessages.ENDPOINT_ID_POSITIVE) Long endpointId
    ) {
        endpointService.delete(userId, endpointId);
        return ResponseEntity.noContent().build();
    }
}
