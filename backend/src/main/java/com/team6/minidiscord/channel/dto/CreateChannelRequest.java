package com.team6.minidiscord.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateChannelRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
        String name
) {
}
