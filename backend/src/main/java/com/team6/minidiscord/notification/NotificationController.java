package com.team6.minidiscord.notification;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.notification.dto.NotificationResponse;
import com.team6.minidiscord.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@RequestParam(required = false) Boolean isRead) {
        return ApiResponse.ok(notificationService.list(SecurityUtils.currentUserId(), isRead));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable String notificationId) {
        return ApiResponse.ok(notificationService.markRead(SecurityUtils.currentUserId(), notificationId));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Map<String, String>> markAllRead() {
        notificationService.markAllRead(SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("message", "Đã đánh dấu tất cả notification là đã đọc."));
    }
}
