package site.yesaido.notification_server.exception;

public class NotificationTemplateMissingException extends RuntimeException {

    public NotificationTemplateMissingException(Long eventTypeId, Long channelTypeId) {
        super("이벤트와 채널에 맞는 알림 템플릿이 없습니다. eventTypeId="
                + eventTypeId + ", channelTypeId=" + channelTypeId);
    }
}
