package com.team6.minidiscord.message;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.common.api.CursorPage;
import com.team6.minidiscord.message.dto.CreateMessageRequest;
import com.team6.minidiscord.message.dto.EditMessageRequest;
import com.team6.minidiscord.message.dto.MessageResponse;
import com.team6.minidiscord.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/channels/{channelId}/messages")
    public ApiResponse<java.util.List<MessageResponse>> history(
            @PathVariable String channelId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        CursorPage<MessageResponse> page = messageService.history(SecurityUtils.currentUserId(), channelId, cursor, limit);
        return ApiResponse.page(page.items(), page.nextCursor());
    }

    @PostMapping("/channels/{channelId}/messages")
    public ApiResponse<MessageResponse> send(@PathVariable String channelId, @Valid @RequestBody CreateMessageRequest request) {
        return ApiResponse.ok(messageService.send(SecurityUtils.currentUserId(), channelId, request));
    }

    @PatchMapping("/messages/{messageId}")
    public ApiResponse<MessageResponse> edit(@PathVariable String messageId, @Valid @RequestBody EditMessageRequest request) {
        return ApiResponse.ok(messageService.edit(SecurityUtils.currentUserId(), messageId, request));
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String messageId) {
        messageService.softDelete(SecurityUtils.currentUserId(), messageId);
        return ApiResponse.ok(Map.of("message", "Đã xóa message."));
    }

    @PutMapping("/messages/{messageId}/reactions/{emoji}")
    public ApiResponse<MessageResponse> addReaction(@PathVariable String messageId, @PathVariable String emoji) {
        return ApiResponse.ok(messageService.addReaction(SecurityUtils.currentUserId(), messageId, emoji));
    }

    @DeleteMapping("/messages/{messageId}/reactions/{emoji}")
    public ApiResponse<MessageResponse> removeReaction(@PathVariable String messageId, @PathVariable String emoji) {
        return ApiResponse.ok(messageService.removeReaction(SecurityUtils.currentUserId(), messageId, emoji));
    }

    @GetMapping("/servers/{serverId}/messages/search")
    public ApiResponse<java.util.List<MessageResponse>> search(
            @PathVariable String serverId,
            @RequestParam String q,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        CursorPage<MessageResponse> page = messageService.search(SecurityUtils.currentUserId(), serverId, q, channelId, cursor, limit);
        return ApiResponse.page(page.items(), page.nextCursor());
    }
}
