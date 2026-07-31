package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@EnabledIfSystemProperty(named = "notification.integration.enabled", matches = "true")
class NotificationEndpointRepositoryTest {

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
    private NotificationEndpointRepository repository;

    @Test
    void 사용자별로_삭제되지_않은_endpoint만_조회한다() {
        insertChannelType(9001L, "TEST_TELEGRAM");
        insertEndpoint(9001L, 9007L, 9001L, true, false, "111");
        insertEndpoint(9002L, 9007L, 9001L, false, true, "222");
        insertEndpoint(9003L, 9008L, 9001L, true, false, "333");

        List<?> endpoints = repository.findAllByUserIdAndDeletedFalse(9007L);

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0)).extracting("destination").isEqualTo("111");
    }

    @Test
    void 사용자와_endpoint_id가_일치하고_삭제되지_않은_경우만_조회한다() {
        insertChannelType(9004L, "TEST_TELEGRAM_2");
        insertEndpoint(9004L, 9010L, 9004L, true, false, "111");
        insertEndpoint(9005L, 9010L, 9004L, true, true, "222");

        assertThat(repository.findByIdAndUserIdAndDeletedFalse(9004L, 9010L)).isPresent();
        assertThat(repository.findByIdAndUserIdAndDeletedFalse(9005L, 9010L)).isEmpty();
        assertThat(repository.findByIdAndUserIdAndDeletedFalse(9004L, 9011L)).isEmpty();
    }

    private void insertChannelType(Long id, String code) {
        // 테스트 DB는 실행 시 Flyway가 스키마를 만들므로 IDE의 정적 스키마 검사를 적용하지 않는다.
        // noinspection SqlResolve
        jdbcTemplate.update("""
                INSERT INTO channel_type (id, code, display_name, is_deleted, created_at, updated_at)
                VALUES (?, ?, ?, FALSE, ?, ?)
                """, id, code, code, LocalDateTime.now(), LocalDateTime.now());
    }

    private void insertEndpoint(Long id, Long userId, Long channelTypeId,
                                boolean enabled, boolean deleted, String destination) {
        // noinspection SqlResolve
        jdbcTemplate.update("""
                INSERT INTO notification_endpoint
                    (id, user_id, channel_type_id, destination, display_name,
                     enabled, is_deleted, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, userId, channelTypeId, destination, "테스트 Endpoint",
                enabled, deleted, LocalDateTime.now(), LocalDateTime.now());
    }
}
