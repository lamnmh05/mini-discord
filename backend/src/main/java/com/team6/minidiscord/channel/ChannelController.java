package com.team6.minidiscord.channel;

import com.team6.minidiscord.channel.dto.ChannelResponse;
import com.team6.minidiscord.channel.dto.CreateChannelRequest;
import com.team6.minidiscord.channel.dto.DeleteChannelRequest;
import com.team6.minidiscord.channel.dto.UpdateChannelRequest;
import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ChannelController {
    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping("/servers/{serverId}/channels")
    public ApiResponse<List<ChannelResponse>> list(@PathVariable String serverId) {
        return ApiResponse.ok(channelService.list(SecurityUtils.currentUserId(), serverId));
    }

    @PostMapping("/servers/{serverId}/channels")
    public ApiResponse<ChannelResponse> create(@PathVariable String serverId, @Valid @RequestBody CreateChannelRequest request) {
        return ApiResponse.ok(channelService.create(SecurityUtils.currentUserId(), serverId, request));
    }

    @PatchMapping("/channels/{channelId}")
    public ApiResponse<ChannelResponse> update(@PathVariable String channelId, @Valid @RequestBody UpdateChannelRequest request) {
        return ApiResponse.ok(channelService.update(SecurityUtils.currentUserId(), channelId, request));
    }

    @DeleteMapping("/channels/{channelId}")
    public ApiResponse<Map<String, String>> delete(
            @PathVariable String channelId,
            @RequestBody(required = false) DeleteChannelRequest request
    ) {
        channelService.softDelete(SecurityUtils.currentUserId(), channelId, request);
        return ApiResponse.ok(Map.of("message", "Đã xóa channel."));
    }
}
