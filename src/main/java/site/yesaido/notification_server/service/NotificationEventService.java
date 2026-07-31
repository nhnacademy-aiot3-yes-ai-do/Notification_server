package site.yesaido.notification_server.service;

import java.util.UUID;
import site.yesaido.notification_server.messaging.DomainEvent;

public interface NotificationEventService {

    EventProcessingResult process(DomainEvent event);

    /**
     * DB UNIQUE 제약으로 중복 저장이 감지됐을 때, 이미 다른 Consumer가 이벤트를
     * 정상 처리했는지 확인한다.
     */
    boolean isProcessed(UUID eventId);
}
