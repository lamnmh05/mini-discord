package com.team6.minidiscord.direct.dto;

import com.team6.minidiscord.friend.dto.FriendUserResponse;

import java.time.Instant;

public record DirectConversationResponse(
        String id,
        FriendUserResponse recipient,
        String lastMessagePreview,
        Instant lastMessageAt,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt
) {
}
