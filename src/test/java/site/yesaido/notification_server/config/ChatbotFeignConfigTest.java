package site.yesaido.notification_server.config;

import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatbotFeignConfigTest {

    @Test
    @DisplayName("Chatbot Feign 클라이언트의 2초 연결, 60초 읽기 타임아웃과 재시도 안 함 설정을 검증한다")
    void chatbotFeignOptionsAndRetryerConfigured() {
        ChatbotFeignConfig config = new ChatbotFeignConfig();

        Request.Options options = config.chatbotFeignOptions();

        assertThat(options.connectTimeoutMillis()).isEqualTo(2_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(60_000);
        assertThat(config.chatbotFeignRetryer()).isEqualTo(Retryer.NEVER_RETRY);
    }
}
