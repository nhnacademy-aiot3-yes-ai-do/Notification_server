package site.yesaido.notification_server.dto.admin;

import site.yesaido.notification_server.entity.NotificationTemplate;

public record NotificationTemplateResponse(
        Long id,
        Long eventTypeId,
        String eventTypeCode,
        Long channelTypeId,
        String channelCode,
        String bodyTemplate,
        int version
) {
    public static NotificationTemplateResponse from(NotificationTemplate t) {
        return new NotificationTemplateResponse(t.getId(), t.getEventType().getId(), t.getEventType().getCode(), t.getChannelType().getId(), t.getChannelType().getCode(), t.getBodyTemplate(), t.getVersion());
    }
}
