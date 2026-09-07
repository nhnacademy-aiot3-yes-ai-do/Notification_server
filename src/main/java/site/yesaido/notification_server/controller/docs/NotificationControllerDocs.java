package site.yesaido.notification_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import site.yesaido.notification_server.dto.delivery.DeliveryPageResponse;

/**
 * {@code NotificationController}의 OpenAPI 문서 정의.
 */
@Tag(name = "알림 내역", description = "본인에게 발송된 알림(Delivery) 내역 조회")
public interface NotificationControllerDocs {

    @Operation(summary = "내 알림 내역 조회", description = "요청자에게 발송된 알림 내역을 페이지 단위로 반환합니다.")
    ResponseEntity<DeliveryPageResponse> findAll(Long userId, @ParameterObject Pageable pageable);
}
