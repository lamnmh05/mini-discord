package com.team6.minidiscord.server;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.membership.dto.ChangeRoleRequest;
import com.team6.minidiscord.membership.dto.MemberResponse;
import com.team6.minidiscord.security.SecurityUtils;
import com.team6.minidiscord.server.dto.CreateServerRequest;
import com.team6.minidiscord.server.dto.ServerResponse;
import com.team6.minidiscord.server.dto.UpdateServerRequest;
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
@RequestMapping("/api/v1/servers")
public class ServerController {
    private final ServerService serverService;
    private final MembershipService membershipService;

    public ServerController(ServerService serverService, MembershipService membershipService) {
        this.serverService = serverService;
        this.membershipService = membershipService;
    }

    @PostMapping
    public ApiResponse<ServerResponse> create(@Valid @RequestBody CreateServerRequest request) {
        return ApiResponse.ok(serverService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<ServerResponse>> list() {
        return ApiResponse.ok(serverService.listJoined(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{serverId}")
    public ApiResponse<ServerResponse> detail(@PathVariable String serverId) {
        return ApiResponse.ok(serverService.detail(SecurityUtils.currentUserId(), serverId));
    }

    @PatchMapping("/{serverId}")
    public ApiResponse<ServerResponse> update(@PathVariable String serverId, @Valid @RequestBody UpdateServerRequest request) {
        return ApiResponse.ok(serverService.update(SecurityUtils.currentUserId(), serverId, request));
    }

    @DeleteMapping("/{serverId}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String serverId) {
        serverService.softDelete(SecurityUtils.currentUserId(), serverId);
        return ApiResponse.ok(Map.of("message", "Đã xóa server."));
    }

    @PostMapping("/{serverId}/leave")
    public ApiResponse<Map<String, String>> leave(@PathVariable String serverId) {
        membershipService.leave(SecurityUtils.currentUserId(), serverId);
        return ApiResponse.ok(Map.of("message", "Đã rời server."));
    }

    @GetMapping("/{serverId}/members")
    public ApiResponse<List<MemberResponse>> members(@PathVariable String serverId) {
        return ApiResponse.ok(membershipService.listMembers(SecurityUtils.currentUserId(), serverId));
    }

    @DeleteMapping("/{serverId}/members/{userId}")
    public ApiResponse<Map<String, String>> kick(@PathVariable String serverId, @PathVariable String userId) {
        membershipService.kick(SecurityUtils.currentUserId(), serverId, userId);
        return ApiResponse.ok(Map.of("message", "Đã kick member."));
    }

    @PatchMapping("/{serverId}/members/{userId}/role")
    public ApiResponse<MemberResponse> changeRole(
            @PathVariable String serverId,
            @PathVariable String userId,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        return ApiResponse.ok(membershipService.changeRole(SecurityUtils.currentUserId(), serverId, userId, request));
    }
}
