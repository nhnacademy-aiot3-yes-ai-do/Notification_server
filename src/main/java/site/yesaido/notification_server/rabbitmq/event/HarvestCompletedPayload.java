package site.yesaido.notification_server.rabbitmq.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HarvestCompletedPayload(
        String cultivationName,
        BigDecimal harvestWeight
) {
}
