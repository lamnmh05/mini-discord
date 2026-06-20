package com.team6.minidiscord.auth.dto;

import com.team6.minidiscord.user.dto.CurrentUserResponse;

public record AuthResponse(
        String accessToken,
        CurrentUserResponse user
) {
}
