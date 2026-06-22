package com.team6.minidiscord.friend;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.friend.dto.FriendRequestResponse;
import com.team6.minidiscord.friend.dto.FriendResponse;
import com.team6.minidiscord.friend.dto.SendFriendRequest;
import com.team6.minidiscord.security.SecurityUtils;
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
@RequestMapping("/api/v1/friends")
public class FriendController {
    private final FriendshipService friendshipService;

    public FriendController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping
    public ApiResponse<List<FriendResponse>> list() {
        return ApiResponse.ok(friendshipService.listFriends(SecurityUtils.currentUserId()));
    }

    @GetMapping("/requests")
    public ApiResponse<List<FriendRequestResponse>> requests() {
        return ApiResponse.ok(friendshipService.listRequests(SecurityUtils.currentUserId()));
    }

    @PostMapping("/requests")
    public ApiResponse<FriendRequestResponse> send(@Valid @RequestBody SendFriendRequest request) {
        return ApiResponse.ok(friendshipService.sendRequest(SecurityUtils.currentUserId(), request));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ApiResponse<FriendResponse> accept(@PathVariable String requestId) {
        return ApiResponse.ok(friendshipService.accept(SecurityUtils.currentUserId(), requestId));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ApiResponse<Map<String, String>> reject(@PathVariable String requestId) {
        friendshipService.reject(SecurityUtils.currentUserId(), requestId);
        return ApiResponse.ok(Map.of("message", "Friend request rejected."));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Map<String, String>> remove(@PathVariable String userId) {
        friendshipService.remove(SecurityUtils.currentUserId(), userId);
        return ApiResponse.ok(Map.of("message", "Friend removed."));
    }
}
