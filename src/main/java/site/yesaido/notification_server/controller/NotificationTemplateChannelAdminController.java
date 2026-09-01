package site.yesaido.notification_server.controller;

import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.notification_server.dto.admin.*;
import site.yesaido.notification_server.service.NotificationTemplateChannelAdminService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class NotificationTemplateChannelAdminController {
    private final NotificationTemplateChannelAdminService service;

    @GetMapping("/notification-templates")
    public ResponseEntity<NotificationTemplateListResponse> templates(@RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(new NotificationTemplateListResponse(service.templates(role)));
    }

    @PostMapping("/notification-templates")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                                       @Valid @RequestBody NotificationTemplateRequest r) {
        return ResponseEntity.status(201).body(service.createTemplate(role, r));
    }

    @PutMapping("/notification-templates/{id}")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody NotificationTemplateRequest r) {
        return ResponseEntity.ok(service.updateTemplate(role, id, r));
    }

    @DeleteMapping("/notification-templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@RequestHeader(value = "X-User-Role", required = false) String role,
                                               @PathVariable Long id) {
        service.deleteTemplate(role, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/channel-types")
    public ResponseEntity<ChannelTypeListResponse> channels(@RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(new ChannelTypeListResponse(service.channels(role)));
    }

    @PostMapping("/channel-types")
    public ResponseEntity<ChannelTypeResponse> createChannel(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                             @Valid @RequestBody ChannelTypeRequest r) {
        return ResponseEntity.status(201).body(service.createChannel(role, r));
    }

    @PutMapping("/channel-types/{id}")
    public ResponseEntity<ChannelTypeResponse> updateChannel(@RequestHeader(value = "X-User-Role", required = false) String role,
                                             @PathVariable Long id,
                                             @Valid @RequestBody ChannelTypeRequest r) {
        return ResponseEntity.ok(service.updateChannel(role, id, r));
    }

    @DeleteMapping("/channel-types/{id}")
    public ResponseEntity<Void> deleteChannel(@RequestHeader(value = "X-User-Role", required = false) String role,
                                              @PathVariable Long id) {
        service.deleteChannel(role, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/channel-types/{id}/restore")
    public ResponseEntity<Void> restoreChannel(@RequestHeader(value = "X-User-Role", required = false) String role,
                                               @PathVariable Long id) {
        service.restoreChannel(role, id);
        return ResponseEntity.noContent().build();
    }
}
