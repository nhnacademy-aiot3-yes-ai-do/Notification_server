package site.yesaido.notification_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import site.yesaido.notification_server.config.ChatbotFeignConfig;
import site.yesaido.notification_server.dto.telegram.ai.TelegramChatbotRequest;
import site.yesaido.notification_server.dto.telegram.ai.TelegramChatbotResponse;

@FeignClient(
        name = "ChatbotFeignClient",
        url = "${notification.access.ai-url}",
        configuration = ChatbotFeignConfig.class
)
public interface ChatbotFeignClient {
    @PostMapping("/api/v1/ai/chat")
    TelegramChatbotResponse chat(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody TelegramChatbotRequest request
    );
}