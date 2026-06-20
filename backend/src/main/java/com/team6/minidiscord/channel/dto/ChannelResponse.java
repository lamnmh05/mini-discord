package com.team6.minidiscord.channel.dto;

import com.team6.minidiscord.channel.ChannelType;

import java.time.Instant;

public record ChannelResponse(
        String id,
        String serverId,
        String name,
        ChannelType type,
        int position,
        boolean defaultChannel,
        Instant createdAt,
        Instant updatedAt
) {
}
