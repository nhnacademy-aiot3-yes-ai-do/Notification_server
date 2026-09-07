package site.yesaido.notification_server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.notification_server.controller.docs.NotificationTemplateChannelAdminControllerDocs;
import site.yesaido.notification_server.dto.admin.*;
import site.yesaido.notification_server.service.NotificationTemplateChannelAdminService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class NotificationTemplateChannelAdminController implements NotificationTemplateChannelAdminControllerDocs {
    private final NotificationTemplateChannelAdminService service;

    @Override    @GetMapping("/notification-templates")
    public ResponseEntity<NotificationTemplateListResponse> templates() {
        return ResponseEntity.ok(new NotificationTemplateListResponse(service.templates()));
    }

    @Override    @PostMapping("/notification-templates")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(@Valid @RequestBody NotificationTemplateRequest r) {
        return ResponseEntity.status(201).body(service.createTemplate(r));
    }

    @Override    @PutMapping("/notification-templates/{id}")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(@PathVariable Long id,
                                                       @Valid @RequestBody NotificationTemplateRequest r) {
        return ResponseEntity.ok(service.updateTemplate(id, r));
    }

    @Override    @DeleteMapping("/notification-templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @Override    @GetMapping("/channel-types")
    public ResponseEntity<ChannelTypeListResponse> channels() {
        return ResponseEntity.ok(new ChannelTypeListResponse(service.channels()));
    }

    @Override    @PostMapping("/channel-types")
    public ResponseEntity<ChannelTypeResponse> createChannel(@Valid @RequestBody ChannelTypeRequest r) {
        return ResponseEntity.status(201).body(service.createChannel(r));
    }

    @Override    @PutMapping("/channel-types/{id}")
    public ResponseEntity<ChannelTypeResponse> updateChannel(@PathVariable Long id,
                                             @Valid @RequestBody ChannelTypeRequest r) {
        return ResponseEntity.ok(service.updateChannel(id, r));
    }

    @Override    @DeleteMapping("/channel-types/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long id) {
        service.deleteChannel(id);
        return ResponseEntity.noContent().build();
    }

    @Override    @PostMapping("/channel-types/{id}/restore")
    public ResponseEntity<Void> restoreChannel(@PathVariable Long id) {
        service.restoreChannel(id);
        return ResponseEntity.noContent().build();
    }
}
