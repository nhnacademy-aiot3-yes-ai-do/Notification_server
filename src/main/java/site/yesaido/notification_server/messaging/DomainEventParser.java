package site.yesaido.notification_server.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.exception.messaging.InvalidDomainEventException;

/**
 * RabbitMQ 메시지 문자열을 공통 이벤트로 변환하고 공통 필드를 검증한다.
 *
 * <p>Producer별 payload 구조와 RabbitMQ 라우팅 정보는 계약 확정 후 Consumer에서
 * 추가로 검증한다.</p>
 */
@Component
public class DomainEventParser {

    private final ObjectMapper objectMapper;

    public DomainEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DomainEvent parse(String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidDomainEventException("Event message must not be blank");
        }

        try {
            DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
            event.validate();
            return event;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidDomainEventException("Invalid domain event message", exception);
        }
    }
}
