package site.yesaido.notification_server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import site.yesaido.notification_server.exception.basic.client.*;
import site.yesaido.notification_server.exception.basic.server.CustomServerException;
import site.yesaido.notification_server.exception.basic.server.ServerErrorLevel;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    //400 Bad Request
    @ExceptionHandler({BadRequestException.class})
    public ErrorResponse handleBadRequestException(BadRequestException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, BadRequestException.getCode(), e.getMessage());
    }

    //401 Unauthorized
    @ExceptionHandler({UnauthorizedException.class})
    public ErrorResponse handleUnauthorizedException(UnauthorizedException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, UnauthorizedException.getCode(), e.getMessage());
    }

    //403 Forbidden
    @ExceptionHandler({ForbiddenException.class})
    public ErrorResponse handleForbiddenExceptionException(ForbiddenException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, ForbiddenException.getCode(), e.getMessage());
    }

    //404 Not Found
    @ExceptionHandler({NotFoundException.class})
    public ErrorResponse handleNotFoundExceptionException(NotFoundException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, NotFoundException.getCode(), e.getMessage());
    }

    //409 Conflict
    @ExceptionHandler({ConflictException.class})
    public ErrorResponse handleConflictException(ConflictException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, ConflictException.getCode(), e.getMessage());
    }

    //415 Unsupported Media Type
    @ExceptionHandler({UnsupportedMediaTypeException.class})
    public ErrorResponse handleUnsupportedMediaTypeException(UnsupportedMediaTypeException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, UnsupportedMediaTypeException.getCode(), e.getMessage());
    }

    private void clientErrorPrint(String logContent) {
        log.info("{}", logContent);
    }

    //500 Custom Server Exception
    @ExceptionHandler({CustomServerException.class})
    public ErrorResponse handleServerException(CustomServerException e) {
        if(e.getErrorLevel().equals(ServerErrorLevel.WARN_LEVEL)) {
            log.warn("{}", e.getLogContent());
        } else {
            log.error("{}", e.getLogContent());
        }

        return createResponseEntity(e, CustomServerException.getStatus(), e.getMessage());
    }

    //500 Server Exception
    @ExceptionHandler({Exception.class})
    public ErrorResponse handleException(Exception e) {
        log.warn("{}", e.getMessage());
        return createResponseEntity(e, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private ErrorResponse createResponseEntity(Exception e, HttpStatus status, String message) {
        return ErrorResponse.create(e, status, message);
    }


    // Spring
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String defaultMessage = (fieldError != null) ? fieldError.getDefaultMessage() : null;
        String message = Objects.requireNonNullElse(defaultMessage, "잘못된 요청입니다.");

        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ErrorResponse handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, "필수 헤더가 누락되었습니다: " + e.getHeaderName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ErrorResponse handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, "사진 파일 크기는 10MB를 초과할 수 없습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse handleIllegalArgumentException(IllegalArgumentException e) {
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, e.getMessage());
    }
}