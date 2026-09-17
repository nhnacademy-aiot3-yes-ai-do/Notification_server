package site.yesaido.notification_server.dto.telegram.ai;

public record TelegramChatbotRequest(
        Long conversationId, // 기존 대화방 번호 (첫 질문이면 null)
        Long cultivationId, // 재배지 ID(지정 안하면 null)
        String message, // 사용자가 텔레그램에 친 질문
        Long channelId // 3L 텔레그램
) {
}
