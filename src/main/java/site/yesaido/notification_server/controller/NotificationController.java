package site.yesaido.notification_server.controller;

import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.delivery.DeliveryResponse;
import site.yesaido.notification_server.service.NotificationQueryService;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;

    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> findAll(
            @RequestHeader("X-User-Id")
            @Positive(message = "사용자 ID는 1 이상이어야 합니다.") Long userId
    ) {
        return ResponseEntity.ok(queryService.findDeliveries(userId));
    }
}
