package com.team6.minidiscord.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DirectInviteRequest(
        @NotBlank
        @Size(max = 32)
        String inviteeUsername
) {
}
