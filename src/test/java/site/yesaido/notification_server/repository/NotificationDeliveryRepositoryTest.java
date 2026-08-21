package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import site.yesaido.notification_server.entity.DeliveryStatus;
import site.yesaido.notification_server.entity.NotificationDelivery;

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

    @Test
    void FAILED_delivery에_성공_upsert를_적용하면_CREATED로_복구되고_템플릿과_메시지가_갱신된다() {
        FanoutIds ids = insertNotificationAndSubscription(99101L);
        insertDelivery(ids.notificationId(), ids.subscriptionId(), null, "FAILED", "", (short) 3, "이전 실패 사유");

        int updated = repository.upsertCreatedFromRabbitMqFanout(
                ids.notificationId(), ids.subscriptionId(), ids.templateId(), "복구된 메시지");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, notification_template_id, rendered_message, attempt_count, error
                FROM notification_delivery
                WHERE notification_id = ? AND notification_subscription_id = ?
                """, ids.notificationId(), ids.subscriptionId());
        assertThat(updated).isEqualTo(1);
        assertThat(row)
                .containsEntry("status", "CREATED")
                .containsEntry("notification_template_id", ids.templateId())
                .containsEntry("rendered_message", "복구된 메시지")
                .containsEntry("error", null);
        assertThat(((Number) row.get("attempt_count")).shortValue()).isZero();
    }

    @Test
    void CREATED_delivery에_성공_upsert를_적용해도_기존_내용이_보존된다() {
        FanoutIds ids = insertNotificationAndSubscription(99102L);
        insertDelivery(ids.notificationId(), ids.subscriptionId(), ids.templateId(), "CREATED", "원본 메시지", (short) 0, null);

        int updated = repository.upsertCreatedFromRabbitMqFanout(
                ids.notificationId(), ids.subscriptionId(), ids.templateId(), "덮어쓰기 시도 메시지");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, rendered_message
                FROM notification_delivery
                WHERE notification_id = ? AND notification_subscription_id = ?
                """, ids.notificationId(), ids.subscriptionId());
        assertThat(updated).isZero();
        assertThat(row)
                .containsEntry("status", "CREATED")
                .containsEntry("rendered_message", "원본 메시지");
    }

    @Test
    void PENDING_delivery에_성공_upsert를_적용해도_기존_내용이_보존된다() {
        FanoutIds ids = insertNotificationAndSubscription(99103L);
        insertDelivery(ids.notificationId(), ids.subscriptionId(), ids.templateId(), "PENDING", "원본 메시지", (short) 1, null);

        int updated = repository.upsertCreatedFromRabbitMqFanout(
                ids.notificationId(), ids.subscriptionId(), ids.templateId(), "덮어쓰기 시도 메시지");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, rendered_message, attempt_count
                FROM notification_delivery
                WHERE notification_id = ? AND notification_subscription_id = ?
                """, ids.notificationId(), ids.subscriptionId());
        assertThat(updated).isZero();
        assertThat(row)
                .containsEntry("status", "PENDING")
                .containsEntry("rendered_message", "원본 메시지");
        assertThat(((Number) row.get("attempt_count")).shortValue()).isEqualTo((short) 1);
    }

    @Test
    void CREATED_delivery에_실패_insert를_시도해도_기존_행이_유지된다() {
        FanoutIds ids = insertNotificationAndSubscription(99104L);
        insertDelivery(ids.notificationId(), ids.subscriptionId(), ids.templateId(), "CREATED", "원본 메시지", (short) 0, null);

        int inserted = repository.insertFailedFromRabbitMqFanout(
                ids.notificationId(), ids.subscriptionId(), ids.templateId(), (short) 3, "새 실패 사유");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, rendered_message, error
                FROM notification_delivery
                WHERE notification_id = ? AND notification_subscription_id = ?
                """, ids.notificationId(), ids.subscriptionId());
        assertThat(inserted).isZero();
        assertThat(row)
                .containsEntry("status", "CREATED")
                .containsEntry("rendered_message", "원본 메시지")
                .containsEntry("error", null);
    }

    @Test
    void SENT_delivery에_실패_insert를_시도해도_기존_행이_유지된다() {
        FanoutIds ids = insertNotificationAndSubscription(99105L);
        insertDelivery(ids.notificationId(), ids.subscriptionId(), ids.templateId(), "SENT", "발송 완료 메시지", (short) 1, null);

        int inserted = repository.insertFailedFromRabbitMqFanout(
                ids.notificationId(), ids.subscriptionId(), ids.templateId(), (short) 3, "새 실패 사유");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, rendered_message, error
                FROM notification_delivery
                WHERE notification_id = ? AND notification_subscription_id = ?
                """, ids.notificationId(), ids.subscriptionId());
        assertThat(inserted).isZero();
        assertThat(row)
                .containsEntry("status", "SENT")
                .containsEntry("rendered_message", "발송 완료 메시지")
                .containsEntry("error", null);
    }

    @Test
    void findPageByUserId는_template이_없는_FAILED_delivery도_반환한다() {
        FanoutIds ids = insertNotificationAndSubscription(99106L);
        insertDelivery(ids.notificationId(), ids.subscriptionId(), null, "FAILED", "", (short) 3, "템플릿 없음");

        Page<NotificationDelivery> page = repository.findPageByUserId(99106L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        NotificationDelivery delivery = page.getContent().get(0);
        assertThat(delivery.getTemplate()).isNull();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getError()).isEqualTo("템플릿 없음");
    }

    private record FanoutIds(Long notificationId, Long subscriptionId, Long templateId) {
    }

    private FanoutIds insertNotificationAndSubscription(Long userId) {
        Long channelTypeId = jdbcTemplate.queryForObject(
                "SELECT id FROM channel_type WHERE code = 'TELEGRAM'", Long.class);
        Long subscriptionTypeId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_subscription_type ORDER BY id LIMIT 1", Long.class);
        Long eventTypeId = jdbcTemplate.queryForObject(
                "SELECT notification_event_type_id FROM notification_subscription_type WHERE id = ?",
                Long.class, subscriptionTypeId);
        Long templateId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_template ORDER BY id LIMIT 1", Long.class);
        Long endpointId = jdbcTemplate.queryForObject("""
                INSERT INTO notification_endpoint
                    (user_id, channel_type_id, destination, display_name, enabled, is_deleted,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'Fanout 테스트 Endpoint', TRUE, FALSE, ?, ?)
                RETURNING id
                """, Long.class, userId, channelTypeId, "fanout-test-" + UUID.randomUUID(),
                LocalDateTime.now(), LocalDateTime.now());
        Long subscriptionId = jdbcTemplate.queryForObject("""
                INSERT INTO notification_subscription
                    (notification_subscription_type_id, notification_endpoint_id, target_id,
                     enabled, is_deleted, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, FALSE, ?, ?)
                RETURNING id
                """, Long.class, subscriptionTypeId, endpointId, userId,
                LocalDateTime.now(), LocalDateTime.now());
        Long notificationId = jdbcTemplate.queryForObject("""
                INSERT INTO notification
                    (source_event_id, notification_event_type_id, event_payload, created_at)
                VALUES (?, ?, CAST(? AS jsonb), ?)
                RETURNING id
                """, Long.class, UUID.randomUUID(), eventTypeId, "{}", LocalDateTime.now());
        return new FanoutIds(notificationId, subscriptionId, templateId);
    }

    private Long insertDelivery(Long notificationId, Long subscriptionId, Long templateId, String status,
                                String renderedMessage, short attemptCount, String error) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notification_delivery
                    (notification_id, notification_subscription_id, notification_template_id,
                     status, rendered_message, attempt_count, error, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, notificationId, subscriptionId, templateId, status, renderedMessage,
                attemptCount, error, LocalDateTime.now(), LocalDateTime.now());
    }

    private Long insertPendingDelivery() {
        Long channelTypeId = jdbcTemplate.queryForObject(
                "SELECT id FROM channel_type WHERE code = 'TELEGRAM'", Long.class);
        Long subscriptionTypeId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_subscription_type ORDER BY id LIMIT 1", Long.class);
        Long eventTypeId = jdbcTemplate.queryForObject(
                "SELECT notification_event_type_id FROM notification_subscription_type WHERE id = ?",
                Long.class, subscriptionTypeId);
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
                INSERT INTO notification
                    (source_event_id, notification_event_type_id, event_payload, created_at)
                VALUES (?, ?, CAST(? AS jsonb), ?)
                RETURNING id
                """, Long.class, UUID.randomUUID(), eventTypeId, "{}", LocalDateTime.now());
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
