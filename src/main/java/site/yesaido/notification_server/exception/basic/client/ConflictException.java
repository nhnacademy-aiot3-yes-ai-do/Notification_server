package site.yesaido.notification_server.exception.basic.client;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ConflictException extends RuntimeException {
    @Getter
    private static final HttpStatus code = HttpStatus.CONFLICT;
    private final String logContent;
    public ConflictException(String message) {
        this(message, message);
    }
    public ConflictException(String message, String logContent) {
        super(message);
        this.logContent = logContent;
    }
}
