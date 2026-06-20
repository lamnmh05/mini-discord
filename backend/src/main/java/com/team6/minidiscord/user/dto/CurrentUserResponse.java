package com.team6.minidiscord.user.dto;

import com.team6.minidiscord.user.AccountStatus;

import java.time.Instant;

public record CurrentUserResponse(
        String id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        String customStatus,
        AccountStatus accountStatus,
        Instant lastSeenAt
) {
}
