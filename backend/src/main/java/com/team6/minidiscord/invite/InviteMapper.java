package com.team6.minidiscord.invite;

import com.team6.minidiscord.invite.dto.InviteCodeResponse;
import com.team6.minidiscord.invite.dto.ServerInviteResponse;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.user.UserDocument;

public final class InviteMapper {
    private InviteMapper() {
    }

    public static InviteCodeResponse code(InviteCodeDocument invite) {
        return new InviteCodeResponse(
                invite.id.toHexString(),
                invite.serverId.toHexString(),
                invite.code,
                invite.maxUses,
                invite.useCount,
                invite.expiresAt,
                invite.revokedAt,
                invite.createdAt
        );
    }

    public static ServerInviteResponse direct(ServerInviteDocument invite, ServerDocument server, UserDocument inviter) {
        return new ServerInviteResponse(
                invite.id.toHexString(),
                invite.serverId.toHexString(),
                server == null ? null : server.name,
                invite.inviterId.toHexString(),
                inviter == null ? null : inviter.username,
                invite.inviteeId.toHexString(),
                invite.status,
                invite.expiresAt,
                invite.respondedAt,
                invite.createdAt
        );
    }
}
