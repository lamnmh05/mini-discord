package com.team6.minidiscord.invite.dto;

import com.team6.minidiscord.invite.InviteStatus;

import java.time.Instant;

public record ServerInviteResponse(
        String id,
        String serverId,
        String serverName,
        String inviterId,
        String inviterUsername,
        String inviteeId,
        InviteStatus status,
        Instant expiresAt,
        Instant respondedAt,
        Instant createdAt
) {
}
