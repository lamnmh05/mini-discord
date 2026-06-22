package com.team6.minidiscord.message;

import com.team6.minidiscord.message.dto.AttachmentInput;
import com.team6.minidiscord.message.dto.AttachmentResponse;
import com.team6.minidiscord.message.dto.MessageResponse;
import com.team6.minidiscord.message.dto.ReactionResponse;
import com.team6.minidiscord.message.dto.SenderSnapshotResponse;

import java.util.ArrayList;
import java.util.List;

public final class MessageMapper {
    private MessageMapper() {
    }

    public static Attachment attachment(AttachmentInput input) {
        Attachment attachment = new Attachment();
        attachment.storageKey = input.storageKey();
        attachment.fileUrl = input.fileUrl();
        attachment.originalName = input.originalName();
        attachment.mimeType = input.mimeType();
        attachment.fileSize = input.fileSize();
        return attachment;
    }

    public static MessageResponse response(MessageDocument message) {
        MessageScope scope = message.scope == null ? MessageScope.SERVER : message.scope;
        return new MessageResponse(
                message.id.toHexString(),
                scope.name(),
                message.serverId == null ? null : message.serverId.toHexString(),
                message.channelId == null ? null : message.channelId.toHexString(),
                message.conversationId == null ? null : message.conversationId.toHexString(),
                message.senderId.toHexString(),
                new SenderSnapshotResponse(
                        message.senderSnapshot.username,
                        message.senderSnapshot.displayName,
                        message.senderSnapshot.avatarUrl
                ),
                message.content,
                message.messageType,
                attachments(message.attachments),
                reactions(message.reactions),
                message.editedAt,
                message.createdAt
        );
    }

    private static List<AttachmentResponse> attachments(List<Attachment> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(a -> new AttachmentResponse(a.storageKey, a.fileUrl, a.originalName, a.mimeType, a.fileSize))
                .toList();
    }

    private static List<ReactionResponse> reactions(List<Reaction> reactions) {
        if (reactions == null) {
            return List.of();
        }
        return reactions.stream()
                .map(r -> new ReactionResponse(r.emoji, r.userIds.stream().map(Object::toString).toList()))
                .toList();
    }

    public static List<Attachment> attachmentsFromInputs(List<AttachmentInput> inputs) {
        if (inputs == null) {
            return new ArrayList<>();
        }
        return inputs.stream().map(MessageMapper::attachment).toList();
    }
}
