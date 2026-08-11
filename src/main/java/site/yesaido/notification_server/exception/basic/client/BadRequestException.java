package site.yesaido.notification_server.exception.basic.client;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BadRequestException extends RuntimeException {
    @Getter
    private static final HttpStatus code = HttpStatus.BAD_REQUEST;
    private final String logContent;
    public BadRequestException(String message) {
        this(message, message);
    }
    public BadRequestException(String message, String logContent) {
        super(message);
        this.logContent = logContent;
    }
}
