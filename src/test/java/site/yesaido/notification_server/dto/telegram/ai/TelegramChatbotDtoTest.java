package site.yesaido.notification_server.dto.telegram.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramChatbotDtoTest {

    @Test
    @DisplayName("TelegramChatbotRequest DTO 생성 및 getter를 검증한다")
    void testRequestDto() {
        TelegramChatbotRequest request = new TelegramChatbotRequest(1L, 2L, "질문", 3L);
        assertThat(request.conversationId()).isEqualTo(1L);
        assertThat(request.cultivationId()).isEqualTo(2L);
        assertThat(request.message()).isEqualTo("질문");
        assertThat(request.channelId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("TelegramChatbotResponse DTO 생성 및 getter를 검증한다")
    void testResponseDto() {
        TelegramChatbotResponse.Data data = new TelegramChatbotResponse.Data(10L, "답변", "ASSISTANT", 1L, "2026-09-17T09:00:00");
        TelegramChatbotResponse response = new TelegramChatbotResponse(true, "성공", data);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("성공");
        assertThat(response.data()).isEqualTo(data);
        assertThat(data.conversationId()).isEqualTo(10L);
        assertThat(data.reply()).isEqualTo("답변");
        assertThat(data.role()).isEqualTo("ASSISTANT");
        assertThat(data.sequenceNumber()).isEqualTo(1L);
        assertThat(data.createdAt()).isEqualTo("2026-09-17T09:00:00");
    }
}
