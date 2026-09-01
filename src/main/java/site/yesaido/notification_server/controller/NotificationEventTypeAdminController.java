package site.yesaido.notification_server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeListResponse;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeRequest;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeResponse;
import site.yesaido.notification_server.service.NotificationEventTypeAdminService;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/notification-event-types")
public class NotificationEventTypeAdminController {

    private final NotificationEventTypeAdminService eventTypeAdminService;

    @GetMapping
    public ResponseEntity<NotificationEventTypeListResponse> findAll() {
        return ResponseEntity.ok(new NotificationEventTypeListResponse(eventTypeAdminService.findAll()));
    }

    @PostMapping
    public ResponseEntity<NotificationEventTypeResponse> create(
            @Valid @RequestBody NotificationEventTypeRequest request) {
        NotificationEventTypeResponse response = eventTypeAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/notification-event-types/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationEventTypeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody NotificationEventTypeRequest request) {
        return ResponseEntity.ok(eventTypeAdminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {
        eventTypeAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
