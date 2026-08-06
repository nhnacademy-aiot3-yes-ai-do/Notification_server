package site.yesaido.notification_server.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.delivery.DeliveryPageResponse;
import site.yesaido.notification_server.service.NotificationQueryService;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;

    @GetMapping
    public ResponseEntity<DeliveryPageResponse> findAll(
            @RequestHeader("X-User-Id")
            @Positive(message = "사용자 ID는 1 이상이어야 합니다.") Long userId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("페이지는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        return ResponseEntity.ok(queryService.findDeliveries(userId, page, size));
    }
}
