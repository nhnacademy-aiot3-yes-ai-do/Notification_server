package site.yesaido.notification_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.yesaido.notification_server.dto.admin.ChannelTypeListResponse;
import site.yesaido.notification_server.dto.admin.ChannelTypeRequest;
import site.yesaido.notification_server.dto.admin.ChannelTypeResponse;
import site.yesaido.notification_server.dto.admin.NotificationTemplateListResponse;
import site.yesaido.notification_server.dto.admin.NotificationTemplateRequest;
import site.yesaido.notification_server.dto.admin.NotificationTemplateResponse;

/**
 * {@code NotificationTemplateChannelAdminController}의 OpenAPI 문서 정의.
 */
@Tag(name = "관리자 - 알림 템플릿/채널", description = "알림 메시지 템플릿과 채널 타입 관리 (관리자 전용)")
public interface NotificationTemplateChannelAdminControllerDocs {

    @Operation(summary = "템플릿 목록", description = "등록된 알림 메시지 템플릿 전체를 반환합니다.")
    ResponseEntity<NotificationTemplateListResponse> templates();

    @Operation(summary = "템플릿 등록", description = "새 알림 메시지 템플릿을 등록합니다.")
    @ApiResponse(responseCode = "201", description = "등록됨")
    ResponseEntity<NotificationTemplateResponse> createTemplate(NotificationTemplateRequest r);

    @Operation(summary = "템플릿 수정", description = "알림 메시지 템플릿을 수정합니다.")
    ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @Parameter(description = "템플릿 ID") Long id, NotificationTemplateRequest r);

    @Operation(summary = "템플릿 삭제", description = "알림 메시지 템플릿을 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    ResponseEntity<Void> deleteTemplate(@Parameter(description = "템플릿 ID") Long id);

    @Operation(summary = "채널 타입 목록", description = "등록된 채널 타입(Telegram/Discord 등) 전체를 반환합니다.")
    ResponseEntity<ChannelTypeListResponse> channels();

    @Operation(summary = "채널 타입 등록", description = "새 채널 타입을 등록합니다.")
    @ApiResponse(responseCode = "201", description = "등록됨")
    ResponseEntity<ChannelTypeResponse> createChannel(ChannelTypeRequest r);

    @Operation(summary = "채널 타입 수정", description = "채널 타입을 수정합니다.")
    ResponseEntity<ChannelTypeResponse> updateChannel(
            @Parameter(description = "채널 타입 ID") Long id, ChannelTypeRequest r);

    @Operation(summary = "채널 타입 삭제", description = "채널 타입을 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    ResponseEntity<Void> deleteChannel(@Parameter(description = "채널 타입 ID") Long id);

    @Operation(summary = "채널 타입 복원", description = "소프트 삭제된 채널 타입을 복원합니다.")
    @ApiResponse(responseCode = "204", description = "복원됨")
    ResponseEntity<Void> restoreChannel(@Parameter(description = "채널 타입 ID") Long id);
}
