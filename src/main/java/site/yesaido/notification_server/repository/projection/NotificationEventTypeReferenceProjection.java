package site.yesaido.notification_server.repository.projection;

public interface NotificationEventTypeReferenceProjection {
    boolean isReferencedBySubscriptionType();

    boolean isReferencedByTemplate();

    boolean isReferencedByNotification();
}
