package site.yesaido.notification_server.service;

import java.util.List;

public record EventProcessingResult(boolean duplicate, List<Long> deliveryIds) {

    public static EventProcessingResult duplicateEvent() {
        return new EventProcessingResult(true, List.of());
    }

    public static EventProcessingResult created(List<Long> deliveryIds) {
        return new EventProcessingResult(false, List.copyOf(deliveryIds));
    }
}
