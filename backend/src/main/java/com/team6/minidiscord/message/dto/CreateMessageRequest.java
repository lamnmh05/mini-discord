package com.team6.minidiscord.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateMessageRequest(
        @Size(max = 4000)
        String content,

        @Valid
        @Size(max = 10)
        List<AttachmentInput> attachments,

        @Size(max = 120)
        String clientRequestId
) {
}
