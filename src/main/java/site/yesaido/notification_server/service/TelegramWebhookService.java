package site.yesaido.notification_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import site.yesaido.notification_server.client.ChatbotFeignClient;
import site.yesaido.notification_server.dto.telegram.TelegramWebhookUpdate;
import site.yesaido.notification_server.dto.telegram.ai.TelegramChatbotRequest;
import site.yesaido.notification_server.dto.telegram.ai.TelegramChatbotResponse;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.provider.TelegramSender;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private static final String PRIVATE_CHAT = "private";
    private static final String START_COMMAND_PREFIX = "/start ";
    // 3 = Telegram
    private static final Long TELEGRAM_AI_CHANNEL_ID = 3L;

    private final TelegramLinkService telegramLinkService;
    private final NotificationEndpointRepository notificationEndpointRepository;
    private final ChannelTypeRepository channelTypeRepository;
    private final TelegramSender telegramSender;
    private final ChatbotFeignClient chatbotFeignClient;

    @Async
    public void handle(TelegramWebhookUpdate update) {
        if (update == null
                || update.message() == null
                || update.message().chat() == null) {
            return;
        }

        TelegramWebhookUpdate.TelegramMessage message = update.message();
        TelegramWebhookUpdate.TelegramChat chat = message.chat();
        String text = message.text();

        if (!PRIVATE_CHAT.equals(chat.type())
                || chat.id() == null
                || text == null
                || text.isBlank()) {
            return;
        }
        // /start 토큰이면 계정 연동 처리
        String token = startToken(text);

        if (token != null) {
            telegramLinkService.completeStart(token, String.valueOf(chat.id()));
            return;
        }
        // 일반 텍스트면 AI 챗봇 처리
        handleChatbot(chat.id(), text);
    }

    private void handleChatbot(Long chatId, String userMessage) {
        try {
            ChannelType telegramChannel =
                    channelTypeRepository
                            .findByCodeAndDeletedFalse("TELEGRAM")
                            .orElse(null);

            if (telegramChannel == null) {
                log.error("TELEGRAM 채널 기준정보가 없습니다.");
                return;
            }

            // Telegram chatId로 연동된 사용자 endpoint 조회
            NotificationEndpoint endpoint =
                    notificationEndpointRepository
                            .findFirstByChannelType_IdAndDestinationAndDeletedFalse(
                                    telegramChannel.getId(),
                                    String.valueOf(chatId)
                            )
                            .orElse(null);

            // 연동되지 않은 사용자
            if (endpoint == null || !endpoint.isEnabled()) {
                telegramSender.send(
                        String.valueOf(chatId),
                        "MushMush 계정과 아직 연동되지 않았습니다.\n"
                                + "웹 마이페이지에서 먼저 텔레그램 연동을 완료해 주세요."
                );
                return;
            }

            Long userId = endpoint.getUserId();

            TelegramChatbotRequest request =
                    new TelegramChatbotRequest(
                            null,
                            null,
                            userMessage,
                            TELEGRAM_AI_CHANNEL_ID
                    );

            TelegramChatbotResponse response =
                    chatbotFeignClient.chat(userId, request);

            String replyText =
                    response != null
                            && response.data() != null
                            && response.data().reply() != null
                            && !response.data().reply().isBlank()
                            ? response.data().reply()
                            : "죄송합니다. AI 답변을 생성하지 못했습니다.";

            telegramSender.send(
                    String.valueOf(chatId),
                    replyText
            );

        } catch (Exception exception) {
            log.error(
                    "텔레그램 챗봇 처리 실패. chatId={}",
                    chatId,
                    exception
            );

            try {
                telegramSender.send(
                        String.valueOf(chatId),
                        "일시적인 오류로 AI 답변을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요."
                );
            } catch (Exception sendException) {
                log.error(
                        "텔레그램 오류 메시지 발송 실패. chatId={}",
                        chatId,
                        sendException
                );
            }
        }
    }


    private String startToken(String text) {
        if (text == null || !text.startsWith(START_COMMAND_PREFIX)) {
            return null;
        }
        String token = text.substring(START_COMMAND_PREFIX.length());
        if(token.isBlank() || token.contains(" ")){
            return null;
        }


        return token;
    }
}
