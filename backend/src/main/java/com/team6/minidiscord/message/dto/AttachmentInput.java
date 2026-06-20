package com.team6.minidiscord.message.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AttachmentInput(
        @NotBlank
        String storageKey,
        @NotBlank
        String fileUrl,
        @NotBlank
        String originalName,
        @NotBlank
        String mimeType,
        @Min(1)
        long fileSize
) {
}
