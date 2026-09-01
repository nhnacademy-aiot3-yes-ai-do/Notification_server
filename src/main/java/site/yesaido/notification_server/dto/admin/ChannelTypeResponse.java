package site.yesaido.notification_server.dto.admin;

import site.yesaido.notification_server.entity.ChannelType;

public record ChannelTypeResponse(
        Long id,
        String code,
        String displayName,
        boolean deleted
) {
    public static ChannelTypeResponse from(ChannelType c) {
        return new ChannelTypeResponse(c.getId(), c.getCode(), c.getDisplayName(), c.isDeleted());
    }
}
