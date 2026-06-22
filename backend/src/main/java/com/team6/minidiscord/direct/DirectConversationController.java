package com.team6.minidiscord.direct;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.common.api.CursorPage;
import com.team6.minidiscord.direct.dto.DirectConversationResponse;
import com.team6.minidiscord.direct.dto.OpenDirectConversationRequest;
import com.team6.minidiscord.message.MessageService;
import com.team6.minidiscord.message.dto.CreateMessageRequest;
import com.team6.minidiscord.message.dto.MessageResponse;
import com.team6.minidiscord.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/direct-conversations")
public class DirectConversationController {
    private final DirectConversationService conversationService;
    private final MessageService messageService;

    public DirectConversationController(DirectConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<List<DirectConversationResponse>> list() {
        return ApiResponse.ok(conversationService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ApiResponse<DirectConversationResponse> open(@Valid @RequestBody OpenDirectConversationRequest request) {
        return ApiResponse.ok(conversationService.open(SecurityUtils.currentUserId(), request.userId()));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> history(
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        CursorPage<MessageResponse> page = messageService.directHistory(SecurityUtils.currentUserId(), conversationId, cursor, limit);
        return ApiResponse.page(page.items(), page.nextCursor());
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<MessageResponse> send(@PathVariable String conversationId, @Valid @RequestBody CreateMessageRequest request) {
        return ApiResponse.ok(messageService.sendDirect(SecurityUtils.currentUserId(), conversationId, request));
    }

    @PatchMapping("/{conversationId}/read")
    public ApiResponse<DirectConversationResponse> markRead(@PathVariable String conversationId) {
        return ApiResponse.ok(conversationService.markRead(SecurityUtils.currentUserId(), conversationId));
    }
}
