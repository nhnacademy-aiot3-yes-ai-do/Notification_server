package site.yesaido.notification_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointEnabledRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;
import site.yesaido.notification_server.validation.ValidationMessages;

import java.util.List;

/**
 * {@code NotificationEndpointController}의 OpenAPI 문서 정의.
 */
@Tag(name = "알림 수신 채널", description = "알림을 받을 채널(Telegram Chat ID · Discord Webhook 등) 등록 · 조회 · 수정 · 삭제")
public interface NotificationEndpointControllerDocs {

    @Operation(summary = "수신 채널 등록", description = "알림 수신 채널(엔드포인트)을 등록합니다. `Location` 헤더로 생성 URI를 반환합니다.")
    @ApiResponse(responseCode = "201", description = "등록됨")
    ResponseEntity<EndpointResponse> create(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Valid EndpointCreateRequest request
    );

    @Operation(summary = "내 수신 채널 목록", description = "요청자가 등록한 수신 채널 목록을 반환합니다.")
    ResponseEntity<List<EndpointResponse>> findAll(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId
    );

    @Operation(summary = "수신 채널 수정", description = "수신 채널 정보를 수정합니다.")
    ResponseEntity<EndpointResponse> update(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Parameter(description = "엔드포인트 ID")
            @Positive(message = ValidationMessages.ENDPOINT_ID_POSITIVE) Long endpointId,
            @Valid EndpointUpdateRequest request);

    @Operation(summary = "수신 채널 사용/일시정지 전환",
            description = "`enabled=false` 로 일시정지, `true` 로 재개합니다. (소프트 삭제와는 별개)")
    ResponseEntity<EndpointResponse> changeEnabled(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Parameter(description = "엔드포인트 ID")
            @Positive(message = ValidationMessages.ENDPOINT_ID_POSITIVE) Long endpointId,
            @Valid EndpointEnabledRequest request);

    @Operation(summary = "수신 채널 삭제", description = "수신 채널을 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    ResponseEntity<Void> delete(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Parameter(description = "엔드포인트 ID")
            @Positive(message = ValidationMessages.ENDPOINT_ID_POSITIVE) Long endpointId);
}
