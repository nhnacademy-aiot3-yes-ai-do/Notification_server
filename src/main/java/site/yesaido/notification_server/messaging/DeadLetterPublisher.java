package site.yesaido.notification_server.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.config.NotificationRabbitConstants;

@Component
public class DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    public DeadLetterPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(Long deliveryId, String reason) {
        try {
            String message = objectMapper.writeValueAsString(
                    new FailedDeliveryMessage(deliveryId, reason, OffsetDateTime.now()));
            rabbitTemplate.convertAndSend(NotificationRabbitConstants.DEAD_LETTER_EXCHANGE, message);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize dead-letter message: deliveryId={}", deliveryId,
                    exception);
        }
    }
}
