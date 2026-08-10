package site.yesaido.notification_server.exception.basic.client;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ForbiddenException extends RuntimeException {
    @Getter
    private static final HttpStatus code = HttpStatus.FORBIDDEN;
    private final String logContent;
    public ForbiddenException(String message) {
        this(message, message);
    }
    public ForbiddenException(String message, String logContent) {
        super(message);
        this.logContent = logContent;
    }
}
