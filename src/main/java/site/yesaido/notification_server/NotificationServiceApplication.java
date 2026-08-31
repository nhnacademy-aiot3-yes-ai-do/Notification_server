package site.yesaido.notification_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import site.yesaido.notification_server.config.property.NotificationProperties;
import site.yesaido.notification_server.config.property.NotificationRecoveryProperties;
import site.yesaido.notification_server.config.property.SubscriptionAccessProperties;
import site.yesaido.notification_server.config.property.TelegramLinkProperties;

@SpringBootApplication
@EnableFeignClients(basePackages = "site.yesaido.notification_server.client")
@EnableScheduling
@EnableConfigurationProperties({
        NotificationProperties.class,
        NotificationRecoveryProperties.class,
        TelegramLinkProperties.class,
        SubscriptionAccessProperties.class
})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
