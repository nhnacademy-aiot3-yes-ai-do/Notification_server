package site.yesaido.notification_server.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsProblemDetailForMissingEndpoint() {
        MockHttpServletRequest request = request("/api/v1/notifications/11");

        ResponseEntity<ProblemDetail> response = handler.handleNotificationApiException(
                new EndpointNotFoundException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("알림 수신 경로를 찾을 수 없습니다.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "ENDPOINT_NOT_FOUND")
                .containsEntry("path", "/api/v1/notifications/11");
    }

    @Test
    void returnsSpecificConflictCodeForDuplicateEndpoint() {
        MockHttpServletRequest request = request("/api/v1/notification-endpoints");

        ResponseEntity<ProblemDetail> response = handler.handleNotificationApiException(
                new DuplicateEndpointException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "ENDPOINT_ALREADY_EXISTS")
                .containsEntry("path", "/api/v1/notification-endpoints");
    }

    @Test
    void returnsFieldMessageForValidationFailure() {
        MockHttpServletRequest request = request("/api/v1/notification-endpoints");
        BindException exception = new BindException(new Object(), "request");
        exception.addError(new FieldError(
                "request", "destination", "알림 수신 주소를 입력해 주세요."));

        ResponseEntity<ProblemDetail> response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail())
                .isEqualTo("destination: 알림 수신 주소를 입력해 주세요.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "INVALID_REQUEST");
    }

    @Test
    void hidesInternalMessageForUnexpectedFailure() {
        MockHttpServletRequest request = request("/api/v1/notifications");

        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new RuntimeException("database password leaked"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("서버 내부 오류가 발생했습니다.");
        assertThat(response.getBody().getDetail()).doesNotContain("password");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
