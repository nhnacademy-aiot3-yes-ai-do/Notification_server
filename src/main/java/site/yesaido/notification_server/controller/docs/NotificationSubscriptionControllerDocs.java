package site.yesaido.notification_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionEnabledRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.validation.ValidationMessages;

import java.util.List;

/**
 * {@code NotificationSubscriptionController}의 OpenAPI 문서 정의.
 */
@Tag(name = "알림 구독", description = "어떤 이벤트를 어떤 대상(재배·문의 등)에 대해 받을지 구독 등록 · 조회 · 전환 · 해지")
public interface NotificationSubscriptionControllerDocs {

    @Operation(summary = "구독 등록",
            description = "이벤트 타입 + 대상 + 수신 채널 조합으로 알림 구독을 등록합니다. "
                    + "삭제되지 않은 동일 조합이 이미 있으면 기존 구독을 활성화합니다. `Location` 헤더로 생성 URI를 반환합니다.")
    @ApiResponse(responseCode = "201", description = "등록됨")
    ResponseEntity<SubscriptionResponse> create(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Valid SubscriptionCreateRequest request);

    @Operation(summary = "내 구독 목록", description = "요청자의 알림 구독 목록을 반환합니다.")
    ResponseEntity<List<SubscriptionResponse>> findAll(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId);

    @Operation(summary = "구독 사용/일시정지 전환", description = "`enabled` 값으로 구독을 일시정지/재개합니다.")
    ResponseEntity<SubscriptionResponse> changeEnabled(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Parameter(description = "구독 ID")
            @Positive(message = ValidationMessages.SUBSCRIPTION_ID_POSITIVE) Long subscriptionId,
            @Valid SubscriptionEnabledRequest request);

    @Operation(summary = "구독 해지", description = "구독을 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "해지됨")
    ResponseEntity<Void> delete(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Parameter(description = "구독 ID")
            @Positive(message = ValidationMessages.SUBSCRIPTION_ID_POSITIVE) Long subscriptionId);
}
