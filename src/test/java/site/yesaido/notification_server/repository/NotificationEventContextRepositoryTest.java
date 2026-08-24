package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
        String eventPayload = """
                {"targetId": 77, "occurredAt": "%s"}
                """.formatted(occurredAt);

        int inserted = repository.insertIfAbsent(eventId, eventTypeId(), eventPayload);
        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT source_event_id,
                       event_payload ->> 'targetId' AS target_id,
                       event_payload ->> 'occurredAt' AS occurred_at
                FROM notification
                WHERE source_event_id = ?
                """, eventId);

        assertThat(inserted).isEqualTo(1);
        assertThat(stored)
                .containsEntry("source_event_id", eventId)
                .containsEntry("target_id", "77")
                .containsEntry("occurred_at", occurredAt.toString());
    }

    @Test
    void 중복_이벤트는_기존_eventPayload를_덮어쓰지_않는다() {
        UUID eventId = UUID.randomUUID();
        Long eventTypeId = eventTypeId();
        String originalPayload = "{\"targetId\":77,\"occurredAt\":\"2026-08-23T12:34:56+09:00\"}";

        assertThat(repository.insertIfAbsent(eventId, eventTypeId, originalPayload)).isEqualTo(1);
        assertThat(repository.insertIfAbsent(eventId, eventTypeId,
                "{\"targetId\":99,\"occurredAt\":\"2026-08-24T12:34:56+09:00\"}"))
                .isZero();

        String storedPayload = jdbcTemplate.queryForObject("""
                SELECT event_payload::text
                FROM notification
                WHERE source_event_id = ?
                """, String.class, eventId);
        assertThat(storedPayload).contains("\"targetId\": 77");
    }

    private Long eventTypeId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notification_event_type ORDER BY id LIMIT 1", Long.class);
    }
}
