package com.team6.minidiscord.direct.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenDirectConversationRequest(
        @NotBlank
        String userId
) {
}
