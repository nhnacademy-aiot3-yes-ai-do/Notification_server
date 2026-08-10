package site.yesaido.notification_server.exception;

import org.springframework.http.HttpStatus;

public enum NotificationErrorCode {

    CHANNEL_TYPE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHANNEL_TYPE_NOT_FOUND",
            "알림 채널을 찾을 수 없습니다."),
    ENDPOINT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ENDPOINT_NOT_FOUND",
            "알림 수신 경로를 찾을 수 없습니다."),
    SUBSCRIPTION_TYPE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SUBSCRIPTION_TYPE_NOT_FOUND",
            "알림 구독 종류를 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SUBSCRIPTION_NOT_FOUND",
            "알림 구독을 찾을 수 없습니다."),
    TARGET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TARGET_NOT_FOUND",
            "알림 대상을 찾을 수 없습니다."),
    ENDPOINT_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "ENDPOINT_ALREADY_EXISTS",
            "이미 등록된 알림 수신 경로입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    NotificationErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
