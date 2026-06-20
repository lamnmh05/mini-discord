package com.team6.minidiscord.server.dto;

import jakarta.validation.constraints.Size;

public record UpdateServerRequest(
        @Size(min = 1, max = 100)
        String name,
        String iconUrl
) {
}
