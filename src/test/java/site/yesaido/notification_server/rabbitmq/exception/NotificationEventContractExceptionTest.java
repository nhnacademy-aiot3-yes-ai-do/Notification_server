package site.yesaido.notification_server.rabbitmq.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

class NotificationEventContractExceptionTest {

    @Test
    void 누락된_eventType은_서버_계약_오류다() {
        NotificationEventTypeNotFoundException exception =
                new NotificationEventTypeNotFoundException("HARVEST_COMPLETED");

        assertThat(exception).isInstanceOf(CustomServerException.class);
        assertThat(((CustomServerException) exception).getErrorLevel())
                .isEqualTo(ServerErrorLevel.ERROR_LEVEL);
    }

    @Test
    void eventTargetType_불일치는_서버_계약_오류다() {
        NotificationEventTargetTypeMismatchException exception =
                new NotificationEventTargetTypeMismatchException("eventCode=HARVEST_COMPLETED");

        assertThat(exception).isInstanceOf(CustomServerException.class);
        assertThat(((CustomServerException) exception).getErrorLevel())
                .isEqualTo(ServerErrorLevel.ERROR_LEVEL);
    }
}
