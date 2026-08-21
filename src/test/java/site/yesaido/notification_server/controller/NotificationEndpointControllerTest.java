package site.yesaido.notification_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;
import site.yesaido.notification_server.exception.GlobalExceptionHandler;
import site.yesaido.notification_server.service.NotificationEndpointService;

@ExtendWith(MockitoExtension.class)
class NotificationEndpointControllerTest {

    @Mock
    private NotificationEndpointService endpointService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationEndpointController(endpointService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsEndpointAndReturnsLocation() throws Exception {
        EndpointResponse response = new EndpointResponse(
                21L, 1L, "TELEGRAM", "Telegram", "12345", "내 텔레그램", true, null, null);
        when(endpointService.create(any(Long.class), any(EndpointCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/notification-endpoints")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content("""
                                {"channelTypeId":1,"destination":"12345","displayName":"내 텔레그램"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/notification-endpoints/21"))
                .andExpect(jsonPath("$.id").value(21));
    }

    @Test
    void listsActiveEndpoints() throws Exception {
        when(endpointService.findAll(7L)).thenReturn(List.of(
                new EndpointResponse(21L, 1L, "TELEGRAM", "Telegram", "12345", "내 텔레그램", true, null, null)));

        mockMvc.perform(get("/api/v1/notification-endpoints").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channelCode").value("TELEGRAM"));
    }

    @Test
    void updatesEndpoint() throws Exception {
        when(endpointService.update(any(Long.class), any(Long.class), any(EndpointUpdateRequest.class)))
                .thenReturn(new EndpointResponse(
                        21L, 1L, "TELEGRAM", "Telegram", "99999", "새 이름", true, null, null));

        mockMvc.perform(patch("/api/v1/notification-endpoints/21")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content("{\"destination\":\"99999\",\"displayName\":\"새 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("99999"));
    }

    @Test
    void validatesEndpointCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/notification-endpoints")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content("{\"channelTypeId\":0,\"destination\":\"\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void deletesEndpointWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/v1/notification-endpoints/21")
                        .header("X-User-Id", "7"))
                .andExpect(status().isNoContent());

        verify(endpointService).delete(7L, 21L);
    }
}
