package site.yesaido.notification_server.rabbitmq.exception;


import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/** 수확 완료 RabbitMQ 이벤트에 알림 템플릿의 필수 수확량이 없는 producer 계약 위반이다. */
public class RabbitMqHarvestQuantityMissingException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "수확 완료 이벤트에 수확량이 없습니다.";

    public RabbitMqHarvestQuantityMissingException(Long cultivationId, Long harvestId) {
        super(
                DEFAULT_MESSAGE,
                "%s - cultivationId=%d, harvestId=%d".formatted(DEFAULT_MESSAGE, cultivationId, harvestId),
                ServerErrorLevel.ERROR_LEVEL);
    }
}
