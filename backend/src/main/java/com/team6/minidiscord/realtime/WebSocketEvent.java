package com.team6.minidiscord.realtime;

import java.time.Instant;
import java.util.UUID;

public record WebSocketEvent<T>(
        int version,
        String eventId,
        String eventType,
        Instant occurredAt,
        String serverId,
        String channelId,
        T data
) {
    public static <T> WebSocketEvent<T> of(String eventType, String serverId, String channelId, T data) {
        return new WebSocketEvent<>(
                1,
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                serverId,
                channelId,
                data
        );
    }
}
