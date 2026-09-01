package site.yesaido.notification_server.dto.admin;

import java.util.List;

public record NotificationTemplateListResponse(
        List<NotificationTemplateResponse> notificationTemplateResponses
) {
 public NotificationTemplateListResponse { notificationTemplateResponses = notificationTemplateResponses == null ? List.of() : List.copyOf(notificationTemplateResponses); }
}
