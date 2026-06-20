package com.team6.minidiscord.membership.dto;

import com.team6.minidiscord.membership.MemberRole;

import java.time.Instant;

public record MemberResponse(
        String userId,
        String username,
        String displayName,
        String avatarUrl,
        String customStatus,
        Instant lastSeenAt,
        String presenceStatus,
        MemberRole role,
        Instant joinedAt
) {
}
