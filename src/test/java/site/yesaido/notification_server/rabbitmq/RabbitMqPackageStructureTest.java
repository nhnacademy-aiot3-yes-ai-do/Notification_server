package site.yesaido.notification_server.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RabbitMqPackageStructureTest {

    @Test
    void 알림_처리_계층은_refactor_중간_패키지_없이_rabbitmq_하위에_있다() throws Exception {
        Path sourceRoot = Path.of("src/main/java/site/yesaido/notification_server/rabbitmq");

        assertThat(Files.exists(sourceRoot.resolve("facade/RabbitMqNotificationFacade.java"))).isTrue();
        assertThat(Files.exists(sourceRoot.resolve("processor/RuleEngineNotificationProcessor.java"))).isTrue();
        assertThat(Files.exists(sourceRoot.resolve("refactor"))).isFalse();
    }
}
