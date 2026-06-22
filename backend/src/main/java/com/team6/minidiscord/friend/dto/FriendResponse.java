package com.team6.minidiscord.friend.dto;

import java.time.Instant;

public record FriendResponse(
        String friendshipId,
        FriendUserResponse user,
        Instant friendsSince
) {
}
