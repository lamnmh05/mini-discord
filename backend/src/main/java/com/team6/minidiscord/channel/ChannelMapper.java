package com.team6.minidiscord.channel;

import com.team6.minidiscord.channel.dto.ChannelResponse;
import org.bson.types.ObjectId;

public final class ChannelMapper {
    private ChannelMapper() {
    }

    public static ChannelResponse response(ChannelDocument channel, ObjectId defaultChannelId) {
        return new ChannelResponse(
                channel.id.toHexString(),
                channel.serverId.toHexString(),
                channel.name,
                channel.type,
                channel.position,
                channel.id.equals(defaultChannelId),
                channel.createdAt,
                channel.updatedAt
        );
    }
}
