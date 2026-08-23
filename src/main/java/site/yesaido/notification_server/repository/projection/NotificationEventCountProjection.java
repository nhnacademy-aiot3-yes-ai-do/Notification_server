package site.yesaido.notification_server.repository.projection;

public interface NotificationEventCountProjection {

    String getEventTypeCode();

    String getEventTypeName();

    long getEventCount();
}
