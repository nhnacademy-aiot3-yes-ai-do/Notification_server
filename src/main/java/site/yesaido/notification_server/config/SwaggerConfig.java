package site.yesaido.notification_server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Notification Server의 OpenAPI(Swagger) 문서 메타데이터를 정의합니다.
 * <p>
 * 엔드포인트별 설명은 {@code controller.docs} 패키지의 {@code XxxControllerDocs} 인터페이스에 두고,
 * 문서 노출 범위와 Swagger UI 경로는 {@code application.yml} 의 springdoc 설정에서 지정합니다.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI notificationOpenAPI(@Value("${server.port:9004}") int serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("Notification Server API")
                        .version("v1")
                        .description("""
                                알림 서비스 API.

                                알림 수신 채널(엔드포인트) 관리 · 알림 구독 관리 · 발송 내역 조회 ·
                                텔레그램 계정 연동 · 알림 이벤트 타입/템플릿/채널 관리(관리자)를 제공합니다.
                                실제 알림 발송은 다른 서비스가 RabbitMQ로 발행한 이벤트를 소비해 처리하며,
                                지원 채널은 Telegram과 Discord입니다.

                                ### 인증
                                모든 요청은 API Gateway를 통해 들어오며, Gateway가 JWT를 검증한 뒤
                                `X-User-Id` 헤더를 주입합니다. 본인 소유의 엔드포인트·구독만 처리됩니다.

                                ### 오류 응답
                                오류는 RFC 7807 `application/problem+json` 형식으로 반환되며
                                `status`, `title`, `detail`, `code`, `timestamp`, `path` 를 포함합니다.

                                ### 문서 범위
                                클러스터 내부 집계 API(`/api/v1/internal/**`)와 Telegram 웹훅(`/webhooks/**`)은 제외됩니다.
                                """))
                .servers(List.of(
                        new Server().url("https://api.yes-nhn.site").description("운영 Gateway"),
                        new Server().url("http://localhost:8000").description("로컬 Gateway"),
                        new Server().url("http://localhost:" + serverPort).description("로컬 직접 호출")
                ));
    }
}
