package com.team6.minidiscord.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendFriendRequest(
        @NotBlank
        @Size(max = 32)
        String username
) {
}
