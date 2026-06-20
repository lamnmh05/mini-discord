package com.team6.minidiscord.invite;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.invite.dto.CreateInviteCodeRequest;
import com.team6.minidiscord.invite.dto.DirectInviteRequest;
import com.team6.minidiscord.invite.dto.InviteCodeResponse;
import com.team6.minidiscord.invite.dto.ServerInviteResponse;
import com.team6.minidiscord.security.SecurityUtils;
import com.team6.minidiscord.server.dto.ServerResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class InviteController {
    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/invite-codes/{code}/join")
    public ApiResponse<ServerResponse> joinByCode(@PathVariable String code) {
        return ApiResponse.ok(inviteService.joinByCode(SecurityUtils.currentUserId(), code));
    }

    @PostMapping("/servers/{serverId}/invite-codes")
    public ApiResponse<InviteCodeResponse> createCode(@PathVariable String serverId, @Valid @RequestBody CreateInviteCodeRequest request) {
        return ApiResponse.ok(inviteService.createCode(SecurityUtils.currentUserId(), serverId, request));
    }

    @GetMapping("/servers/{serverId}/invite-codes")
    public ApiResponse<List<InviteCodeResponse>> listCodes(@PathVariable String serverId) {
        return ApiResponse.ok(inviteService.listCodes(SecurityUtils.currentUserId(), serverId));
    }

    @DeleteMapping("/invite-codes/{inviteCodeId}")
    public ApiResponse<Map<String, String>> revoke(@PathVariable String inviteCodeId) {
        inviteService.revokeCode(SecurityUtils.currentUserId(), inviteCodeId);
        return ApiResponse.ok(Map.of("message", "Đã revoke invite code."));
    }

    @PostMapping("/servers/{serverId}/direct-invites")
    public ApiResponse<ServerInviteResponse> directInvite(@PathVariable String serverId, @Valid @RequestBody DirectInviteRequest request) {
        return ApiResponse.ok(inviteService.sendDirectInvite(SecurityUtils.currentUserId(), serverId, request));
    }

    @GetMapping("/server-invites/received")
    public ApiResponse<List<ServerInviteResponse>> received() {
        return ApiResponse.ok(inviteService.received(SecurityUtils.currentUserId()));
    }

    @PostMapping("/server-invites/{inviteId}/accept")
    public ApiResponse<ServerResponse> accept(@PathVariable String inviteId) {
        return ApiResponse.ok(inviteService.accept(SecurityUtils.currentUserId(), inviteId));
    }

    @PostMapping("/server-invites/{inviteId}/reject")
    public ApiResponse<Map<String, String>> reject(@PathVariable String inviteId) {
        inviteService.reject(SecurityUtils.currentUserId(), inviteId);
        return ApiResponse.ok(Map.of("message", "Đã từ chối invite."));
    }
}
