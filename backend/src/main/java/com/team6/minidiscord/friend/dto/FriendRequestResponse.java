package com.team6.minidiscord.friend.dto;

import java.time.Instant;

public record FriendRequestResponse(
        String id,
        FriendUserResponse user,
        FriendRequestDirection direction,
        Instant requestedAt
) {
}
