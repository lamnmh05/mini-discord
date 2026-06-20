package com.team6.minidiscord.server;

import com.team6.minidiscord.membership.MemberRole;
import com.team6.minidiscord.server.dto.ServerResponse;

public final class ServerMapper {
    private ServerMapper() {
    }

    public static ServerResponse response(ServerDocument server, MemberRole role) {
        return new ServerResponse(
                server.id.toHexString(),
                server.name,
                server.iconUrl,
                server.defaultChannelId == null ? null : server.defaultChannelId.toHexString(),
                role,
                server.createdAt,
                server.updatedAt
        );
    }
}
