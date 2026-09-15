package site.yesaido.notification_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import site.yesaido.notification_server.controller.docs.NotificationEndpointControllerDocs;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointEnabledRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;
import site.yesaido.notification_server.service.NotificationEndpointService;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-endpoints")
public class NotificationEndpointController implements NotificationEndpointControllerDocs {

    private final NotificationEndpointService endpointService;

    @Override    @PostMapping
    public ResponseEntity<EndpointResponse> create(
            @RequestHeader("X-User-Id")
            Long userId,
            @RequestBody EndpointCreateRequest request
    ) {
        EndpointResponse response = endpointService.create(userId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/notification-endpoints/" + response.id())).body(response);
    }

    @Override    @GetMapping
    public ResponseEntity<List<EndpointResponse>> findAll(
            @RequestHeader("X-User-Id")
            Long userId
    ) {
        return ResponseEntity.ok(endpointService.findAll(userId));
    }

    @Override    @PatchMapping("/{endpointId}")
    public ResponseEntity<EndpointResponse> update(
            @RequestHeader("X-User-Id")
            Long userId,
            @PathVariable
            Long endpointId,
            @RequestBody EndpointUpdateRequest request
    ) {
        return ResponseEntity.ok(endpointService.update(userId, endpointId, request));
    }

    @Override    @PatchMapping("/{endpointId}/enabled")
    public ResponseEntity<EndpointResponse> changeEnabled(
            @RequestHeader("X-User-Id")
            Long userId,
            @PathVariable
            Long endpointId,
            @RequestBody EndpointEnabledRequest request
    ) {
        return ResponseEntity.ok(
                endpointService.changeEnabled(userId, endpointId, request.enabled()));
    }

    @Override    @DeleteMapping("/{endpointId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id")
            Long userId,
            @PathVariable
            Long endpointId
    ) {
        endpointService.delete(userId, endpointId);
        return ResponseEntity.noContent().build();
    }
}
