package site.yesaido.notification_server.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import site.yesaido.common.exception.client.*;
import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("400 Bad Request")
    class BadRequest {

        @Test
        @DisplayName("Bad Request Exception")
        void handleBadRequestException() {
            String message = "test-message";
            BadRequestException exception = new BadRequestException(message);

            ErrorResponse response = handler.handleBadRequestException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }

        @Test
        @DisplayName("Method Argument Not Valid Exception")
        void handleMethodArgumentNotValidException() {
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError fieldError = new FieldError("objectName", "field", "test-message");

            when(bindingResult.getFieldError()).thenReturn(fieldError);
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

            ErrorResponse response = handler.handleMethodArgumentNotValidException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()),
                    () -> Assertions.assertEquals("test-message", Objects.requireNonNull(response.getBody()).getDetail())
            );
        }

        @Test
        @DisplayName("Missing Request Header Exception")
        void handleMissingRequestHeaderException() {
            MissingRequestHeaderException exception = new MissingRequestHeaderException("X-Test-Header", null);

            ErrorResponse response = handler.handleMissingRequestHeaderException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()),
                    () -> Assertions.assertEquals("필수 헤더가 누락되었습니다: X-Test-Header", Objects.requireNonNull(response.getBody()).getDetail())
            );
        }
    }

    @Nested
    @DisplayName("401 Unauthorized")
    class Unauthorized {

        @Test
        @DisplayName("Unauthorized Exception")
        void handleUnauthorizedException() {
            String message = "test-message";
            UnauthorizedException exception = new UnauthorizedException(message);

            ErrorResponse response = handler.handleUnauthorizedException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }
    }

    @Nested
    @DisplayName("403 Forbidden")
    class Forbidden {

        @Test
        @DisplayName("Forbidden Exception")
        void handleForbiddenException() {
            String message = "test-message";
            ForbiddenException exception = new ForbiddenException(message);

            ErrorResponse response = handler.handleForbiddenExceptionException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }
    }

    @Nested
    @DisplayName("404 Not Found")
    class NotFound {

        @Test
        @DisplayName("Not Found Exception")
        void handleNotFoundException() {
            String message = "test-message";
            NotFoundException exception = new NotFoundException(message);

            ErrorResponse response = handler.handleNotFoundExceptionException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }
    }

    @Nested
    @DisplayName("409 Conflict")
    class Conflict {

        @Test
        @DisplayName("Conflict Exception")
        void handleConflictException() {
            String message = "test-message";
            ConflictException exception = new ConflictException(message);

            ErrorResponse response = handler.handleConflictException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.CONFLICT, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }
    }

    @Nested
    @DisplayName("415 Unsupported Media Type")
    class UnsupportedMediaType {

        @Test
        @DisplayName("Unsupported Media Type Exception")
        void handleUnsupportedMediaTypeException() {
            String message = "test-message";
            UnsupportedMediaTypeException exception = new UnsupportedMediaTypeException(message);

            ErrorResponse response = handler.handleUnsupportedMediaTypeException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }
    }

    @Nested
    @DisplayName("500 Custom Server")
    class CustomServer {

        @Test
        @DisplayName("Custom Server Exception - WARN")
        void handleCustomServerExceptionWarn() {
            String message = "test-message";
            ServerErrorLevel level = ServerErrorLevel.WARN_LEVEL;
            CustomServerException exception = new CustomServerException(message, level);

            ErrorResponse response = handler.handleServerException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }

        @Test
        @DisplayName("Custom Server Exception - ERROR")
        void handleCustomServerExceptionError() {
            String message = "test-message";
            ServerErrorLevel level = ServerErrorLevel.ERROR_LEVEL;
            CustomServerException exception = new CustomServerException(message, level);

            ErrorResponse response = handler.handleServerException(exception);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()),
                    () -> Assertions.assertEquals(message, Objects.requireNonNull(response.getBody()).getDetail())
            );
        }
    }

    @Test
    @DisplayName("500 Server Exception")
    void handleServerException() {
        String message = "test-message";
        RuntimeException exception = new RuntimeException(message);

        ErrorResponse response = handler.handleException(exception);

        Assertions.assertTrue(Objects.requireNonNull(Objects.requireNonNull(response.getBody()).getDetail()).contains("서버 오류가 발생했습니다."));
    }
}
