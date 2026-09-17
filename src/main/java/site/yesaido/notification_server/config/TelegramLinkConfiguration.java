package site.yesaido.notification_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TelegramLinkConfiguration {

    @Bean
    Clock telegramLinkClock() {
        return Clock.systemUTC();
    }
}
