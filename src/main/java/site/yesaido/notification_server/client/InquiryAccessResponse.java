package site.yesaido.notification_server.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InquiryAccessResponse(Boolean allowed) {
}
