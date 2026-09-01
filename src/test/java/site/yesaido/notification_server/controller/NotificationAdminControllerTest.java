package site.yesaido.notification_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.yesaido.common.exception.client.ConflictException;
import site.yesaido.common.exception.client.ForbiddenException;
import site.yesaido.notification_server.dto.admin.ChannelTypeRequest;
import site.yesaido.notification_server.dto.admin.ChannelTypeResponse;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeRequest;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeResponse;
import site.yesaido.notification_server.dto.admin.NotificationTemplateRequest;
import site.yesaido.notification_server.dto.admin.NotificationTemplateResponse;
import site.yesaido.notification_server.exception.GlobalExceptionHandler;
import site.yesaido.notification_server.service.NotificationEventTypeAdminService;
import site.yesaido.notification_server.service.NotificationTemplateChannelAdminService;

@ExtendWith(MockitoExtension.class)
class NotificationAdminControllerTest {

    @Mock
    private NotificationEventTypeAdminService eventService;
    @Mock
    private NotificationTemplateChannelAdminService templateChannelService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new NotificationEventTypeAdminController(eventService),
                        new NotificationTemplateChannelAdminController(templateChannelService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void eventListIsWrapped() throws Exception {
        when(eventService.findAll("ADMIN")).thenReturn(List.of(
                new NotificationEventTypeResponse(1L, "HARVEST_COMPLETED", "수확 완료", "설명", "USER")));

        mockMvc.perform(get("/api/v1/admin/notification-event-types").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationEventTypeResponses[0].code").value("HARVEST_COMPLETED"));
    }

    @Test
    void templateAndChannelListsAreWrapped() throws Exception {
        when(templateChannelService.templates("ADMIN")).thenReturn(List.of(
                new NotificationTemplateResponse(2L, 1L, "WELCOME", 3L, "EMAIL", "본문", 1)));
        when(templateChannelService.channels("ADMIN")).thenReturn(List.of(
                new ChannelTypeResponse(3L, "EMAIL", "이메일", false)));

        mockMvc.perform(get("/api/v1/admin/notification-templates").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationTemplateResponses[0].id").value(2));
        mockMvc.perform(get("/api/v1/admin/channel-types").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channelTypeResponses[0].code").value("EMAIL"));
    }

    @Test
    void updateResponsesAreResponseEntities() throws Exception {
        when(templateChannelService.updateTemplate(any(), any(Long.class), any(NotificationTemplateRequest.class)))
                .thenReturn(new NotificationTemplateResponse(2L, 1L, "WELCOME", 3L, "EMAIL", "수정 본문", 1));
        when(templateChannelService.updateChannel(any(), any(Long.class), any(ChannelTypeRequest.class)))
                .thenReturn(new ChannelTypeResponse(3L, "EMAIL", "새 이름", false));

        mockMvc.perform(put("/api/v1/admin/notification-templates/2")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventTypeId\":1,\"channelTypeId\":3,\"bodyTemplate\":\"수정 본문\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyTemplate").value("수정 본문"));
        mockMvc.perform(put("/api/v1/admin/channel-types/3")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMAIL\",\"displayName\":\"새 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("새 이름"));
    }

    @Test
    void missingAdminRoleIsForbidden() throws Exception {
        when(templateChannelService.channels(null)).thenThrow(new ForbiddenException("관리자 권한이 필요합니다."));

        mockMvc.perform(get("/api/v1/admin/channel-types"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTemplateIdsAreBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notification-templates")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bodyTemplate\":\"본문\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceConflictBecomesConflictResponse() throws Exception {
        doThrow(new ConflictException("이미 등록된 채널 코드입니다."))
                .when(templateChannelService).createChannel(any(), any(ChannelTypeRequest.class));

        mockMvc.perform(post("/api/v1/admin/channel-types")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMAIL\",\"displayName\":\"이메일\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturnsNoContentAndDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/channel-types/3").header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
        verify(templateChannelService).deleteChannel("ADMIN", 3L);
    }
}
