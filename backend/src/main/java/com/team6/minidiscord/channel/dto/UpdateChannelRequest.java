package com.team6.minidiscord.channel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateChannelRequest(
        @Size(min = 1, max = 64)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
        String name,

        @Min(0)
        Integer position
) {
}
