package site.yesaido.notification_server.config;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;
// AI 응답 생성 오래걸릴 수 있으니 비동기 처리하여 timeout 60초 설정
public class ChatbotFeignConfig {
    @Bean
    public Request.Options chatbotFeignOptions(){
        return new Request.Options(
                2_000,
                TimeUnit.MILLISECONDS,
                60_000,
                TimeUnit.MILLISECONDS,
                true
        );
    }

    @Bean
    public Retryer chatbotFeignRetryer(){
        return Retryer.NEVER_RETRY;
    }
}
