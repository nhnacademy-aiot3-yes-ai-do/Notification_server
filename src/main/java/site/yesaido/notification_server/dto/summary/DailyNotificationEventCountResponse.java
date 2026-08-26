package site.yesaido.notification_server.dto.summary;

public record DailyNotificationEventCountResponse(
        String eventTypeCode,
        String eventTypeName,
        long count
) {
}
