package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import site.yesaido.notification_server.domain.NotificationDelivery;

@DataJpaTest
@EnabledIfSystemProperty(named = "notification.integration.enabled", matches = "true")
class NotificationDeliveryRepositoryTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationDeliveryRepository repository;

    @Test
    void 같은_pending_delivery는_조건부_update로_한번만_선점된다() {
        Long deliveryId = insertPendingDelivery();

        int firstClaim = repository.claimPendingDelivery(
                deliveryId, NotificationDelivery.MAX_ATTEMPT_COUNT);
        int secondClaim = repository.claimPendingDelivery(
                deliveryId, NotificationDelivery.MAX_ATTEMPT_COUNT);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isZero();
        assertThat(status).isEqualTo("SENDING");
    }

    @Test
    void 시도를_모두_소진한_오래된_sending_delivery는_최종실패로_한번만_처리된다() {
        Long deliveryId = insertPendingDelivery();
        jdbcTemplate.update("""
                UPDATE notification_delivery
                SET status = 'SENDING', attempt_count = ?, updated_at = ?
                WHERE id = ?
                """, NotificationDelivery.MAX_ATTEMPT_COUNT,
                LocalDateTime.now().minusMinutes(10), deliveryId);

        int firstFinalize = repository.failStaleSendingDeliveryWhenAttemptsExhausted(
                deliveryId,
                LocalDateTime.now().minusMinutes(5),
                "복구 중 시도 횟수 소진 확인",
                NotificationDelivery.MAX_ATTEMPT_COUNT);
        int secondFinalize = repository.failStaleSendingDeliveryWhenAttemptsExhausted(
                deliveryId,
                LocalDateTime.now().minusMinutes(5),
                "복구 중 시도 횟수 소진 확인",
                NotificationDelivery.MAX_ATTEMPT_COUNT);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
        assertThat(firstFinalize).isEqualTo(1);
        assertThat(secondFinalize).isZero();
        assertThat(status).isEqualTo("FAILED");
    }

    private Long insertPendingDelivery() {
        Long channelTypeId = jdbcTemplate.queryForObject(
                "SELECT id FROM channel_type WHERE code = 'TELEGRAM'", Long.class);
        Long subscriptionTypeId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_subscription_type ORDER BY id LIMIT 1", Long.class);
        Long templateId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_template ORDER BY id LIMIT 1", Long.class);
        Long endpointId = jdbcTemplate.queryForObject("""
                INSERT INTO notification_endpoint
                    (user_id, channel_type_id, destination, display_name, enabled, is_deleted,
                     created_at, updated_at)
                VALUES (?, ?, ?, '선점 테스트 Endpoint', TRUE, FALSE, ?, ?)
                RETURNING id
                """, Long.class, 99001L, channelTypeId, "claim-test-" + UUID.randomUUID(),
                LocalDateTime.now(), LocalDateTime.now());
        Long subscriptionId = jdbcTemplate.queryForObject("""
                INSERT INTO notification_subscription
                    (notification_subscription_type_id, notification_endpoint_id, target_id,
                     enabled, is_deleted, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, FALSE, ?, ?)
                RETURNING id
                """, Long.class, subscriptionTypeId, endpointId, 99001L,
                LocalDateTime.now(), LocalDateTime.now());
        Long notificationId = jdbcTemplate.queryForObject("""
                INSERT INTO notification (source_event_id, event_payload, created_at)
                VALUES (?, CAST(? AS jsonb), ?)
                RETURNING id
                """, Long.class, UUID.randomUUID(), "{}", LocalDateTime.now());
        return jdbcTemplate.queryForObject("""
                INSERT INTO notification_delivery
                    (notification_id, notification_subscription_id, notification_template_id,
                     status, rendered_message, attempt_count, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING', '선점 검증 메시지', 0, ?, ?)
                RETURNING id
                """, Long.class, notificationId, subscriptionId, templateId,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
