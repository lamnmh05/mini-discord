package com.team6.minidiscord.friend.dto;

import java.time.Instant;

public record FriendUserResponse(
        String userId,
        String username,
        String displayName,
        String avatarUrl,
        String customStatus,
        Instant lastSeenAt,
        String presenceStatus
) {
}
