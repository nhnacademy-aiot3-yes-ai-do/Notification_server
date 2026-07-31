package site.yesaido.notification_server.service;

import site.yesaido.notification_server.messaging.DomainEvent;

public interface NotificationEventService {

    EventProcessingResult process(DomainEvent event);
}
