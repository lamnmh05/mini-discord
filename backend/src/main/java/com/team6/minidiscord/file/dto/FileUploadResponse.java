package com.team6.minidiscord.file.dto;

public record FileUploadResponse(
        String storageKey,
        String fileUrl,
        String originalName,
        String mimeType,
        long fileSize
) {
}
