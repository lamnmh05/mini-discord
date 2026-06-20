package com.team6.minidiscord.notification.dto;

import com.team6.minidiscord.notification.NotificationType;

import java.time.Instant;
import java.util.Map;

public record NotificationResponse(
        String id,
        String actorId,
        String serverInviteId,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> data,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
}
