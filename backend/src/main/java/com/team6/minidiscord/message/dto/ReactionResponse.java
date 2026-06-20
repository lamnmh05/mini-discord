package com.team6.minidiscord.message.dto;

import java.util.List;

public record ReactionResponse(
        String emoji,
        List<String> userIds
) {
}
