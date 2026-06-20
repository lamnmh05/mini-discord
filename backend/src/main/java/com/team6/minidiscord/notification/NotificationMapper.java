package com.team6.minidiscord.notification;

import com.team6.minidiscord.notification.dto.NotificationResponse;

public final class NotificationMapper {
    private NotificationMapper() {
    }

    public static NotificationResponse response(NotificationDocument notification) {
        return new NotificationResponse(
                notification.id.toHexString(),
                notification.actorId == null ? null : notification.actorId.toHexString(),
                notification.serverInviteId == null ? null : notification.serverInviteId.toHexString(),
                notification.type,
                notification.title,
                notification.body,
                notification.data,
                notification.isRead,
                notification.readAt,
                notification.createdAt
        );
    }
}
