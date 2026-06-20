package com.team6.minidiscord.message.dto;

import com.team6.minidiscord.message.MessageType;

import java.time.Instant;
import java.util.List;

public record MessageResponse(
        String id,
        String serverId,
        String channelId,
        String senderId,
        SenderSnapshotResponse senderSnapshot,
        String content,
        MessageType messageType,
        List<AttachmentResponse> attachments,
        List<ReactionResponse> reactions,
        Instant editedAt,
        Instant createdAt
) {
}
