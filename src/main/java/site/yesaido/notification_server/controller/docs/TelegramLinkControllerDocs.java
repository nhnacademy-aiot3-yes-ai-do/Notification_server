package site.yesaido.notification_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import site.yesaido.notification_server.dto.telegram.TelegramLinkSessionResponse;
import site.yesaido.notification_server.dto.telegram.TelegramLinkStatusResponse;
import site.yesaido.notification_server.validation.ValidationMessages;

import java.util.UUID;

/**
 * {@code TelegramLinkController}의 OpenAPI 문서 정의.
 */
@Tag(name = "텔레그램 연동", description = "텔레그램 봇과 사용자 계정을 연결하는 연동 세션 생성 및 상태 조회")
public interface TelegramLinkControllerDocs {

    @Operation(summary = "연동 상태 조회",
            description = "연동 세션의 진행 상태(대기/완료/만료 등)를 반환합니다. 프론트가 폴링으로 완료를 확인합니다.")
    ResponseEntity<TelegramLinkStatusResponse> status(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @Parameter(description = "연동 세션 ID (UUID)") UUID sessionId);

    @Operation(summary = "연동 세션 생성",
            description = "새 텔레그램 연동 세션을 생성합니다. 응답의 딥링크로 사용자가 봇 대화를 시작하면 연동이 완료됩니다. "
                    + "`Location` 헤더로 세션 조회 URI를 반환합니다.")
    @ApiResponse(responseCode = "201", description = "생성됨")
    ResponseEntity<TelegramLinkSessionResponse> create(
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId);
}
