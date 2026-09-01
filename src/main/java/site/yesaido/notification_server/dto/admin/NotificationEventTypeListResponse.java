package site.yesaido.notification_server.dto.admin;

import java.util.List;

public record NotificationEventTypeListResponse(
        List<NotificationEventTypeResponse> notificationEventTypeResponses
) {
 public NotificationEventTypeListResponse { notificationEventTypeResponses = notificationEventTypeResponses == null ? List.of() : List.copyOf(notificationEventTypeResponses); }
}
