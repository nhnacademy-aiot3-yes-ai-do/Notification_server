package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import site.yesaido.notification_server.domain.ChannelType;
import site.yesaido.notification_server.domain.NotificationEndpoint;
import site.yesaido.notification_server.domain.NotificationSubscription;
import site.yesaido.notification_server.domain.NotificationSubscriptionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@EnabledIfSystemProperty(named = "notification.integration.enabled", matches = "true")
class NotificationSubscriptionRepositoryTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty(
                "notification.integration.db-url",
                "jdbc:postgresql://localhost:55432/notification_migration_test"));
        registry.add("spring.datasource.username", () -> System.getProperty(
                "notification.integration.db-username", "postgres"));
        registry.add("spring.datasource.password", () -> System.getProperty(
                "notification.integration.db-password", "postgres"));
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NotificationSubscriptionRepository repository;

    @Test
    void 일시정지한_구독은_새로_만들지_않고_기존_구독을_다시_사용한다() {
        ChannelType channelType = entityManager.createQuery(
                        "select c from ChannelType c where c.code = :code", ChannelType.class)
                .setParameter("code", "TELEGRAM")
                .getSingleResult();
        NotificationSubscriptionType subscriptionType = entityManager.createQuery(
                        """
                        select s from NotificationSubscriptionType s
                        where s.eventType.code = :eventCode
                          and s.targetType.targetType = :targetType
                        """, NotificationSubscriptionType.class)
                .setParameter("eventCode", "SENSOR_ERROR")
                .setParameter("targetType", "CULTIVATION")
                .getSingleResult();

        NotificationEndpoint endpoint = new NotificationEndpoint(
                91001L, channelType, "test-chat-91001", "통합 테스트");
        entityManager.persist(endpoint);

        NotificationSubscription paused = new NotificationSubscription(
                subscriptionType, endpoint, 92001L);
        paused.changeEnabled(false);
        entityManager.persist(paused);
        entityManager.flush();

        assertThat(repository
                .findBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndDeletedFalse(
                        subscriptionType.getId(), endpoint.getId(), 92001L))
                .contains(paused);

        NotificationSubscription duplicate = new NotificationSubscription(
                subscriptionType, endpoint, 92001L);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
