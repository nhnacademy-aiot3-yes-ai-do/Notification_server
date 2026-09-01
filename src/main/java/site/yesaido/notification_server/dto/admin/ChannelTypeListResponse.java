package site.yesaido.notification_server.dto.admin;

import java.util.List;

public record ChannelTypeListResponse(
        List<ChannelTypeResponse> channelTypeResponses
) {
 public ChannelTypeListResponse { channelTypeResponses = channelTypeResponses == null ? List.of() : List.copyOf(channelTypeResponses); }
}
