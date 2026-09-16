package site.yesaido.notification_server.dto.telegram.ai;

public record TelegramChatbotResponse(
        boolean success,
        String message,
        Data data
) {
    public record Data(
            Long conversationId,
            String reply, // AI가 생성한 최종 답변 텍스트
            String role, // ASSISTANT
            Long sequenceNumber,
            String createdAt
    ){}
}
