package com.team6.minidiscord.message.dto;

public record SenderSnapshotResponse(
        String username,
        String displayName,
        String avatarUrl
) {
}
