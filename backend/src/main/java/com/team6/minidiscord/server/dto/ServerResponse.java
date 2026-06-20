package com.team6.minidiscord.server.dto;

import com.team6.minidiscord.membership.MemberRole;

import java.time.Instant;

public record ServerResponse(
        String id,
        String name,
        String iconUrl,
        String defaultChannelId,
        MemberRole currentRole,
        Instant createdAt,
        Instant updatedAt
) {
}
