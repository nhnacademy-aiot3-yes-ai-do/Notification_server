package site.yesaido.notification_server.rabbitmq.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class RabbitMqNotificationTransactionBoundaryTest {

    @Test
    void notificationAndEachDeliveryAreCommittedInIndependentTransactions() throws NoSuchMethodException {
        assertRequiresNew(RabbitMqNotificationCreationService.class, "createIfAbsent");
        assertRequiresNew(RabbitMqNotificationDeliveryPersistenceService.class, "persist");
        assertRequiresNew(RabbitMqNotificationDeliveryPersistenceService.class, "activateForDispatch");
    }

    @Test
    void orchestrationKeepsReadTransactionOpenForLazyRelationshipAccess() throws NoSuchMethodException {
        Method persist = RabbitMqNotificationPersistenceService.class
                .getDeclaredMethod("persist", site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand.class);
        Transactional transactional = persist.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequiresNew(Class<?> type, String methodName) throws NoSuchMethodException {
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
