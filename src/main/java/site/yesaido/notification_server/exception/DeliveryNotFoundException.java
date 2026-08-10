package site.yesaido.notification_server.exception;

public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(Long deliveryId) {
        super("발송 이력을 찾을 수 없습니다. deliveryId=" + deliveryId);
    }
}
