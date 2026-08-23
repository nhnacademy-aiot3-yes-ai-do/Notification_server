package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@EnabledIfSystemProperty(named = "notification.integration.enabled", matches = "true")
class NotificationEventContextRepositoryTest {

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
    private NotificationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void RabbitMQ_원본_저장시_대상과_이벤트_발생시각을_보존한다() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-23T12:34:56+09:00");

        int inserted = repository.insertIfAbsent(eventId, eventTypeId(), 77L, occurredAt, "{}");
        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT source_event_id, target_id, occurred_at
                FROM notification
                WHERE source_event_id = ?
                """, eventId);

        assertThat(inserted).isEqualTo(1);
        assertThat(stored)
                .containsEntry("source_event_id", eventId)
                .containsEntry("target_id", 77L);
        assertThat(((Timestamp) stored.get("occurred_at")).toInstant())
                .isEqualTo(occurredAt.toInstant());
    }

    @Test
    void targetId만_저장하면_거부한다() {
        assertThatThrownBy(() -> repository.insertIfAbsent(
                UUID.randomUUID(), eventTypeId(), 77L, null, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void occurredAt만_저장하면_거부한다() {
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-23T12:34:56+09:00");

        assertThatThrownBy(() -> repository.insertIfAbsent(
                UUID.randomUUID(), eventTypeId(), null, occurredAt, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long eventTypeId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notification_event_type ORDER BY id LIMIT 1", Long.class);
    }
}
