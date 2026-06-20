package com.team6.minidiscord.invite.dto;

import java.time.Instant;

public record InviteCodeResponse(
        String id,
        String serverId,
        String code,
        Integer maxUses,
        int useCount,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt
) {
}
