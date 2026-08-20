package site.yesaido.notification_server.messaging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.yesaido.notification_server.rabbitmq.RabbitMQConstants;

class DeadLetterPublisherTest {

    @Test
    void 실패정보를_JSON으로_직렬화해_DLQ로_발행한다() throws Exception {
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        DeadLetterPublisher publisher = new DeadLetterPublisher(rabbitTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any(FailedDeliveryMessage.class)))
                .thenReturn("{\"deliveryId\":7}");

        publisher.publish(7L, "provider timeout");

        ArgumentCaptor<FailedDeliveryMessage> messageCaptor =
                ArgumentCaptor.forClass(FailedDeliveryMessage.class);
        verify(objectMapper).writeValueAsString(messageCaptor.capture());
        assertNotNull(messageCaptor.getValue().failedAt());
        verify(rabbitTemplate).convertAndSend(
                RabbitMQConstants.DLX_NAME,
                RabbitMQConstants.DLQ_ROUTING_KEY,
                "{\"deliveryId\":7}");
    }

    @Test
    void 직렬화에_실패하면_깨진_메시지를_DLQ로_보내지_않는다() throws Exception {
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        DeadLetterPublisher publisher = new DeadLetterPublisher(rabbitTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any(FailedDeliveryMessage.class)))
                .thenThrow(new JsonProcessingException("serialization failed") { });

        publisher.publish(7L, "provider timeout");

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
    }
}
