package site.yesaido.notification_server.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            NotificationNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "NOTIFICATION_RESOURCE_NOT_FOUND",
                exception.getMessage(), request);
    }

    @ExceptionHandler({
        DuplicateNotificationResourceException.class,
        DataIntegrityViolationException.class
    })
    ResponseEntity<ProblemDetail> handleConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        log.warn("Notification resource conflict: path={}", request.getRequestURI());
        return response(HttpStatus.CONFLICT, "NOTIFICATION_RESOURCE_CONFLICT",
                "이미 존재하거나 현재 상태와 충돌하는 요청입니다.", request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class
    })
    ResponseEntity<ProblemDetail> handleValidation(
            BindException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class
    })
    ResponseEntity<ProblemDetail> handleBadRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "요청 형식이나 값이 올바르지 않습니다.", request);
    }

    @ExceptionHandler(UnsupportedNotificationChannelException.class)
    ResponseEntity<ProblemDetail> handleUnsupportedChannel(
            UnsupportedNotificationChannelException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "UNSUPPORTED_NOTIFICATION_CHANNEL",
                exception.getMessage(), request);
    }

    @ExceptionHandler(NotificationProviderException.class)
    ResponseEntity<ProblemDetail> handleProvider(
            NotificationProviderException exception,
            HttpServletRequest request
    ) {
        log.error("Notification provider request failed: path={}", request.getRequestURI(), exception);
        return response(HttpStatus.BAD_GATEWAY, "NOTIFICATION_PROVIDER_FAILURE",
                "외부 알림 채널 요청에 실패했습니다.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected notification API error: path={}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다.", request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("path", request.getRequestURI());
        return ResponseEntity.status(status).body(problem);
    }
}
