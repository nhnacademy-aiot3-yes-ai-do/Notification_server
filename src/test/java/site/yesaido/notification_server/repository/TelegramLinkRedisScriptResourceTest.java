package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TelegramLinkRedisScriptResourceTest {

    @Test
    void packagesAllTelegramLinkLuaScriptsAsClasspathResources() {
        List<String> scriptPaths = List.of(
                "redis/telegram-link/create-link.lua",
                "redis/telegram-link/complete-link.lua",
                "redis/telegram-link/complete-after-commit.lua",
                "redis/telegram-link/release-lock.lua");

        scriptPaths.forEach(path -> assertThat(new ClassPathResource(path).exists())
                .as(path)
                .isTrue());
    }
}
