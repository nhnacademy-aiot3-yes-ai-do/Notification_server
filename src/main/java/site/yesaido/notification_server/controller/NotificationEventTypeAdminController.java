package site.yesaido.notification_server.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeListResponse;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeRequest;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeResponse;
import site.yesaido.notification_server.service.NotificationEventTypeAdminService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/notification-event-types")
public class NotificationEventTypeAdminController {

    private final NotificationEventTypeAdminService eventTypeAdminService;

    @GetMapping
    public ResponseEntity<NotificationEventTypeListResponse> findAll(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(new NotificationEventTypeListResponse(eventTypeAdminService.findAll(role)));
    }

    @PostMapping
    public ResponseEntity<NotificationEventTypeResponse> create(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody NotificationEventTypeRequest request) {
        NotificationEventTypeResponse response = eventTypeAdminService.create(role, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/notification-event-types/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationEventTypeResponse> update(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody NotificationEventTypeRequest request) {
        return ResponseEntity.ok(eventTypeAdminService.update(role, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id) {
        eventTypeAdminService.delete(role, id);
        return ResponseEntity.noContent().build();
    }
}
