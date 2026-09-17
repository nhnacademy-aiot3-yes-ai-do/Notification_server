package site.yesaido.notification_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.notification_server.client.ChatbotFeignClient;
import site.yesaido.notification_server.dto.telegram.TelegramWebhookUpdate;
import site.yesaido.notification_server.dto.telegram.ai.TelegramChatbotRequest;
import site.yesaido.notification_server.dto.telegram.ai.TelegramChatbotResponse;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.provider.TelegramSender;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookServiceTest {

    @Mock
    private TelegramLinkService telegramLinkService;

    @Mock
    private NotificationEndpointRepository notificationEndpointRepository;

    @Mock
    private ChannelTypeRepository channelTypeRepository;

    @Mock
    private TelegramSender telegramSender;

    @Mock
    private ChatbotFeignClient chatbotFeignClient;

    @Mock
    private TelegramChatLockService telegramChatLockService;

    @InjectMocks
    private TelegramWebhookService telegramWebhookService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient()
                .when(telegramChatLockService.tryLock(any(), any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("/start <token> 명령어가 들어오면 계정 연동을 완료한다")
    void completesLinkForPrivateChatStartCommand() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        telegramWebhookService.handle(update);

        verify(telegramLinkService).completeStart("opaque-token", "123456");
        verify(chatbotFeignClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("비공개(private) 채팅이 아닌 경우 /start 명령어를 무시한다")
    void ignoresStartCommandFromNonPrivateChat() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token",
                        new TelegramWebhookUpdate.TelegramChat(-100L, "group")));

        telegramWebhookService.handle(update);

        verify(telegramLinkService, never()).completeStart(any(), any());
        verify(chatbotFeignClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("메시지 형식이 올바르지 않거나 빈 메시지인 경우 무시한다")
    void ignoresMalformedOrBlankUpdates() {
        telegramWebhookService.handle(null);
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L, null));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token", null)));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token",
                        new TelegramWebhookUpdate.TelegramChat(null, "private"))));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private"))));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("   ",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private"))));

        verify(telegramLinkService, never()).completeStart(any(), any());
        verify(chatbotFeignClient, never()).chat(any(), any());
        verify(telegramSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("TELEGRAM 채널 기준정보가 DB에 없으면 처리를 중단하고 발송하지 않는다")
    void handle_whenTelegramChannelNotFound_doesNotCallSenderOrAi() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("오늘 버섯 상태 어때?",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.empty());

        telegramWebhookService.handle(update);

        verify(notificationEndpointRepository, never()).findFirstByChannelType_IdAndDestinationAndDeletedFalse(any(), any());
        verify(chatbotFeignClient, never()).chat(any(), any());
        verify(telegramSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("연동된 엔드포인트가 없는 사용자라면 계정 연동 안내 메시지를 발송한다")
    void handle_whenEndpointNotFound_sendsUnlinkedNotice() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("오늘 습도 알려줘",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.empty());

        telegramWebhookService.handle(update);

        verify(telegramSender).send(
                "123456",
                "MushMush 계정과 아직 연동되지 않았습니다.\n웹 마이페이지에서 먼저 텔레그램 연동을 완료해 주세요."
        );
        verify(chatbotFeignClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("엔드포인트가 비활성화(disabled) 상태인 사용자라면 계정 연동 안내 메시지를 발송한다")
    void handle_whenEndpointDisabled_sendsUnlinkedNotice() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("오늘 습도 알려줘",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        NotificationEndpoint endpoint = new NotificationEndpoint(100L, telegramChannel, "123456", "내 텔레그램");
        endpoint.changeEnabled(false);

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.of(endpoint));

        telegramWebhookService.handle(update);

        verify(telegramSender).send(
                "123456",
                "MushMush 계정과 아직 연동되지 않았습니다.\n웹 마이페이지에서 먼저 텔레그램 연동을 완료해 주세요."
        );
        verify(chatbotFeignClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("연동된 사용자가 질문하면 AI 서버 챗봇 API를 호출하고 응답 결과를 텔레그램으로 전송한다")
    void handle_whenUserLinked_callsChatbotAndSendsAiReply() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("현재 생육실 온도는?",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        NotificationEndpoint endpoint = new NotificationEndpoint(100L, telegramChannel, "123456", "내 텔레그램");

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.of(endpoint));

        TelegramChatbotRequest expectedRequest = new TelegramChatbotRequest(null, null, "현재 생육실 온도는?", 3L);
        TelegramChatbotResponse mockResponse = new TelegramChatbotResponse(
                true,
                "OK",
                new TelegramChatbotResponse.Data(1L, "현재 생육실 온도는 18.5도입니다.", "ASSISTANT", 1L, "2026-09-15T00:00:00")
        );

        when(chatbotFeignClient.chat(100L, expectedRequest)).thenReturn(mockResponse);

        telegramWebhookService.handle(update);

        verify(chatbotFeignClient).chat(100L, expectedRequest);
        verify(telegramSender).send("123456", "현재 생육실 온도는 18.5도입니다.");
    }

    @Test
    @DisplayName("AI 응답 내용이 비어있으면 기본 안내 문구를 전송한다")
    void handle_whenAiResponseEmpty_sendsDefaultFallbackMessage() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("질문",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        NotificationEndpoint endpoint = new NotificationEndpoint(100L, telegramChannel, "123456", "내 텔레그램");

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.of(endpoint));

        TelegramChatbotResponse emptyResponse = new TelegramChatbotResponse(
                true,
                "OK",
                new TelegramChatbotResponse.Data(1L, "", "ASSISTANT", 1L, "2026-09-15T00:00:00")
        );
        when(chatbotFeignClient.chat(any(), any())).thenReturn(emptyResponse);

        telegramWebhookService.handle(update);

        verify(telegramSender).send("123456", "죄송합니다. AI 답변을 생성하지 못했습니다.");
    }

    @Test
    @DisplayName("AI 서버 호출 중 예외가 발생하면 사용자에게 오류 안내 메시지를 발송한다")
    void handle_whenChatbotClientThrowsException_sendsErrorGuidanceMessage() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("질문",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        NotificationEndpoint endpoint = new NotificationEndpoint(100L, telegramChannel, "123456", "내 텔레그램");

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.of(endpoint));

        when(chatbotFeignClient.chat(any(), any())).thenThrow(new RuntimeException("Feign Read Timeout"));

        telegramWebhookService.handle(update);

        verify(telegramSender).send("123456", "일시적인 오류로 AI 답변을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    @DisplayName("오류 안내 메시지 전송까지 실패하더라도 예외를 밖으로 던지지 않는다")
    void handle_whenErrorGuidanceSendThrowsException_doesNotThrow() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("질문",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        NotificationEndpoint endpoint = new NotificationEndpoint(100L, telegramChannel, "123456", "내 텔레그램");

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.of(endpoint));

        when(chatbotFeignClient.chat(any(), any())).thenThrow(new RuntimeException("Feign Read Timeout"));
        when(telegramSender.send(any(), any())).thenThrow(new RuntimeException("Telegram Network Error"));

        assertDoesNotThrow(() -> telegramWebhookService.handle(update));
    }

    @Test
    @DisplayName("/start 뒤에 공백만 있거나 공백이 포함된 비정상 토큰이면 챗봇 처리 흐름으로 전환된다")
    void handle_whenStartTokenInvalidFormat_proceedsToChatbot() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start token with space",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.empty());

        telegramWebhookService.handle(update);

        verify(telegramLinkService, never()).completeStart(any(), any());
        verify(telegramSender).send(
                "123456",
                "MushMush 계정과 아직 연동되지 않았습니다.\n웹 마이페이지에서 먼저 텔레그램 연동을 완료해 주세요."
        );
    }

    @Test
    @DisplayName("AI 응답 객체 자체가 null이거나 data 필드가 null이면 기본 안내 문구를 전송한다")
    void handle_whenAiResponseNull_sendsDefaultFallbackMessage() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("질문",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);

        NotificationEndpoint endpoint = new NotificationEndpoint(100L, telegramChannel, "123456", "내 텔레그램");

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM"))
                .thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.of(endpoint));

        when(chatbotFeignClient.chat(any(), any())).thenReturn(null);

        telegramWebhookService.handle(update);

        verify(telegramSender).send("123456", "죄송합니다. AI 답변을 생성하지 못했습니다.");
    }

    @Test
    @DisplayName("정상 처리 후 finally 블록에서 락을 해제한다")
    void handle_whenSuccessful_releasesLock() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start valid-token",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        telegramWebhookService.handle(update);

        verify(telegramChatLockService).releaseLock(eq(123456L), anyString());
    }

    @Test
    @DisplayName("처리 도중 예외가 발생해도 finally 블록에서 락이 반드시 해제된다")
    void handle_whenExceptionOccurs_stillReleasesLock() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("질문",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        ChannelType telegramChannel = new ChannelType("TELEGRAM", "Telegram");
        ReflectionTestUtils.setField(telegramChannel, "id", 3L);
        NotificationEndpoint endpoint = new NotificationEndpoint(100L, telegramChannel, "123456", "내 텔레그램");

        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM")).thenReturn(Optional.of(telegramChannel));
        when(notificationEndpointRepository.findFirstByChannelType_IdAndDestinationAndDeletedFalse(3L, "123456"))
                .thenReturn(Optional.of(endpoint));
        when(chatbotFeignClient.chat(any(), any())).thenThrow(new RuntimeException("AI server error"));

        telegramWebhookService.handle(update);

        // 예외 상황에서도 락 해제 호출 검증
        verify(telegramChatLockService).releaseLock(eq(123456L), anyString());
    }

    @Test
    @DisplayName("락 획득 실패 시 안내 메시지를 보내며, 발송 중 예외가 나도 안전하게 종료된다")
    void handle_whenLockFailsAndSendThrows_handlesGracefully() {
        when(telegramChatLockService.tryLock(any(), any(), any(), any())).thenReturn(false);
        doThrow(new RuntimeException("Telegram API timeout")).when(telegramSender).send(any(), any());

        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("질문",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        assertDoesNotThrow(() -> telegramWebhookService.handle(update));

        verify(chatbotFeignClient, never()).chat(any(), any());
        verify(telegramChatLockService, never()).releaseLock(any(), any());
    }

    @Test
    @DisplayName("/start 처리 중 예외 발생 시 안내 메시지를 발송하고 finally 블록에서 락을 해제한다")
    void handle_whenStartProcessingThrows_sendsNoticeAndReleasesLock() {
        when(telegramLinkService.completeStart(any(), any()))
                .thenThrow(new RuntimeException("link failed"));

        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start valid-token",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        assertDoesNotThrow(() -> telegramWebhookService.handle(update));

        verify(telegramSender).send(
                "123456",
                "계정 연동 처리 중 오류가 발생했습니다. 마이페이지에서 다시 시도해 주세요."
        );
        verify(telegramChatLockService).releaseLock(eq(123456L), anyString());
    }

    @Test
    @DisplayName("/start 처리 중 예외 및 안내 메시지 발송 실패 시에도 안전하게 락이 해제된다")
    void handle_whenStartThrowsAndNoticeThrows_stillReleasesLock() {
        when(telegramLinkService.completeStart(any(), any()))
                .thenThrow(new RuntimeException("link failed"));
        doThrow(new RuntimeException("Telegram API error"))
                .when(telegramSender).send(any(), any());

        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start valid-token",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        assertDoesNotThrow(() -> telegramWebhookService.handle(update));

        verify(telegramChatLockService).releaseLock(eq(123456L), anyString());
    }
}
