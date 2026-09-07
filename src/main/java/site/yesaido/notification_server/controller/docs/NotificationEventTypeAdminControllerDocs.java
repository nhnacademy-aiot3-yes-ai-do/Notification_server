package site.yesaido.notification_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeListResponse;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeRequest;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeResponse;

/**
 * {@code NotificationEventTypeAdminController}의 OpenAPI 문서 정의.
 */
@Tag(name = "관리자 - 알림 이벤트 타입", description = "알림 이벤트 타입 목록 · 등록 · 수정 · 삭제 (관리자 전용)")
public interface NotificationEventTypeAdminControllerDocs {

    @Operation(summary = "이벤트 타입 목록", description = "등록된 알림 이벤트 타입 전체를 반환합니다.")
    ResponseEntity<NotificationEventTypeListResponse> findAll();

    @Operation(summary = "이벤트 타입 등록", description = "새 알림 이벤트 타입을 등록합니다. `Location` 헤더로 생성 URI를 반환합니다.")
    @ApiResponse(responseCode = "201", description = "등록됨")
    ResponseEntity<NotificationEventTypeResponse> create(NotificationEventTypeRequest request);

    @Operation(summary = "이벤트 타입 수정", description = "알림 이벤트 타입을 수정합니다.")
    ResponseEntity<NotificationEventTypeResponse> update(
            @Parameter(description = "이벤트 타입 ID") Long id,
            NotificationEventTypeRequest request);

    @Operation(summary = "이벤트 타입 삭제", description = "알림 이벤트 타입을 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    ResponseEntity<Void> delete(@Parameter(description = "이벤트 타입 ID") Long id);
}
