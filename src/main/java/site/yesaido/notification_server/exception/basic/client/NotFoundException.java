package site.yesaido.notification_server.exception.basic.client;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class NotFoundException extends RuntimeException {
    @Getter
    private static final HttpStatus code = HttpStatus.NOT_FOUND;
    private final String logContent;
    public NotFoundException(String message) {
        this(message, message);
    }
    public NotFoundException(String message, String logContent) {
        super(message);
        this.logContent = logContent;
    }
}
