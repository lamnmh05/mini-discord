package com.team6.minidiscord.message.dto;

public record AttachmentResponse(
        String storageKey,
        String fileUrl,
        String originalName,
        String mimeType,
        long fileSize
) {
}
