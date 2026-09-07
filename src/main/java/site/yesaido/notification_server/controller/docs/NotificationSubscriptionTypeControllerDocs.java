package site.yesaido.notification_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;

import java.util.List;

/**
 * {@code NotificationSubscriptionTypeController}의 OpenAPI 문서 정의.
 */
@Tag(name = "알림 구독 타입", description = "구독 가능한 알림 이벤트 타입 목록 조회")
public interface NotificationSubscriptionTypeControllerDocs {

    @Operation(summary = "구독 타입 목록 조회", description = "사용자가 구독할 수 있는 알림 이벤트 타입 목록을 반환합니다.")
    ResponseEntity<List<SubscriptionTypeResponse>> findAll();
}
