package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void 실제_ObjectMapper_JSONB_저장값으로_발생시각_경계와_재배지를_기준으로_집계한다() throws Exception {
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
        insertRawNotification(eventTypeId, "{\"targetId\":\"4\",\"occurredAt\":\"2026-08-20T05:00:00+09:00\"}");
        insertRawNotification(eventTypeId, "{\"targetId\":4,\"occurredAt\":\"not-a-date\"}");

        List<NotificationEventCountProjection> result = repository
                .countEventsByCultivationAndOccurredAtBetween(4L, START_AT, END_AT);

        assertThat(result).extracting(NotificationEventCountProjection::getEventTypeCode,
                        NotificationEventCountProjection::getEventCount)
                .containsExactly(
                        tuple("ENVIRONMENT_RECOVERED", 1L),
                        tuple("ENVIRONMENT_THRESHOLD_BREACHED", 3L));
    }

    private void insertRawNotification(Long eventTypeId, String eventPayload) {
        jdbcTemplate.update("""
                INSERT INTO notification
                    (source_event_id, notification_event_type_id, event_payload)
                VALUES (?, ?, CAST(? AS jsonb))
                """, UUID.randomUUID(), eventTypeId, eventPayload);
    }

    private Long eventTypeId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notification_event_type WHERE code = ?", Long.class, code);
    }

    private void insertNotification(Long eventTypeId, Long targetId, OffsetDateTime occurredAt)
            throws Exception {
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("targetId", targetId);
        eventPayload.put("occurredAt", occurredAt);
        jdbcTemplate.update("""
                INSERT INTO notification
                    (source_event_id, notification_event_type_id, event_payload)
                VALUES (?, ?, CAST(? AS jsonb))
                """, UUID.randomUUID(), eventTypeId, objectMapper.writeValueAsString(eventPayload));
    }
}
