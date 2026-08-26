package site.yesaido.notification_server.repository.projection;

public interface NotificationEventCountProjection {

    Long getCultivationId();

    String getEventTypeCode();

    String getEventTypeName();

    Long getEventCount();
}
