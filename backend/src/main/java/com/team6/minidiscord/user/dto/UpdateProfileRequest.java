package com.team6.minidiscord.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
        String username,

        @Size(max = 80)
        String displayName,

        @Size(max = 180)
        String customStatus,

        String avatarUrl
) {
}
