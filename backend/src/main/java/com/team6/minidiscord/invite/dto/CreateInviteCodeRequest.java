package com.team6.minidiscord.invite.dto;

import jakarta.validation.constraints.Min;

import java.time.Instant;

public record CreateInviteCodeRequest(
        @Min(1)
        Integer maxUses,
        Instant expiresAt
) {
}
