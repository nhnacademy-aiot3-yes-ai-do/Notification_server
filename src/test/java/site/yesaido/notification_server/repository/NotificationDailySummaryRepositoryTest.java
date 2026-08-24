package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import site.yesaido.notification_server.repository.projection.NotificationEventCountProjection;

@DataJpaTest
@EnabledIfSystemProperty(named = "notification.integration.enabled", matches = "true")
class NotificationDailySummaryRepositoryTest {

    private static final OffsetDateTime START_AT = OffsetDateTime.parse("2026-08-20T00:00:00+09:00");
    private static final OffsetDateTime END_AT = OffsetDateTime.parse("2026-08-21T00:00:00+09:00");

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
    private NotificationRepository repository;

    @Test
    void 발생시각_경계와_재배지를_기준으로_원본알림만_유형별_집계한다() {
        Long eventTypeId = eventTypeId("ENVIRONMENT_THRESHOLD_BREACHED");
        Long recoveredTypeId = eventTypeId("ENVIRONMENT_RECOVERED");
        insertNotification(eventTypeId, 4L, START_AT);
        insertNotification(eventTypeId, 4L, START_AT.plusHours(1));
        insertNotification(eventTypeId, 4L, END_AT.minusSeconds(1));
        insertNotification(recoveredTypeId, 4L, START_AT.plusHours(2));
        insertNotification(eventTypeId, 4L, END_AT);
        insertNotification(eventTypeId, 5L, START_AT.plusHours(2));
        insertNotification(eventTypeId("LOGIN_SUCCEEDED"), 4L, START_AT.plusHours(3));
        insertNotification(eventTypeId("INQUIRY_ANSWERED"), 4L, START_AT.plusHours(4));

        List<NotificationEventCountProjection> result = repository
                .countEventsByCultivationAndOccurredAtBetween(4L, START_AT, END_AT);

        assertThat(result).extracting(NotificationEventCountProjection::getEventTypeCode,
                        NotificationEventCountProjection::getEventCount)
                .containsExactly(
                        tuple("ENVIRONMENT_RECOVERED", 1L),
                        tuple("ENVIRONMENT_THRESHOLD_BREACHED", 3L));
    }

    private Long eventTypeId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notification_event_type WHERE code = ?", Long.class, code);
    }

    private void insertNotification(Long eventTypeId, Long targetId, OffsetDateTime occurredAt) {
        String eventPayload = """
                {"targetId": %d, "occurredAt": "%s"}
                """.formatted(targetId, occurredAt);
        jdbcTemplate.update("""
                INSERT INTO notification
                    (source_event_id, notification_event_type_id, event_payload)
                VALUES (?, ?, CAST(? AS jsonb))
                """, UUID.randomUUID(), eventTypeId, eventPayload);
    }
}
