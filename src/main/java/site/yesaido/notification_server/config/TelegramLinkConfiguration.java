package site.yesaido.notification_server.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramLinkConfiguration {

    @Bean
    Clock telegramLinkClock() {
        return Clock.systemUTC();
    }
}
