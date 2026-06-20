package com.team6.minidiscord.notification;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.notification.dto.NotificationResponse;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public NotificationDocument createServerInvite(ObjectId inviteeId, ObjectId inviterId, ObjectId serverInviteId, String serverName) {
        NotificationDocument notification = new NotificationDocument();
        notification.userId = inviteeId;
        notification.actorId = inviterId;
        notification.serverInviteId = serverInviteId;
        notification.type = NotificationType.SERVER_INVITE;
        notification.title = "Lời mời tham gia server";
        notification.body = "Bạn được mời tham gia server " + serverName;
        notification.data = Map.of("serverInviteId", serverInviteId.toHexString());
        notification.isRead = false;
        notification.createdAt = Instant.now();
        return notificationRepository.save(notification);
    }

    public List<NotificationResponse> list(ObjectId userId, Boolean isRead) {
        List<NotificationDocument> notifications = isRead == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead);
        return notifications.stream().map(NotificationMapper::response).toList();
    }

    public NotificationResponse markRead(ObjectId userId, String notificationIdValue) {
        NotificationDocument notification = notificationRepository.findByIdAndUserId(ObjectIds.parse(notificationIdValue), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Notification không tồn tại."));
        markRead(notification);
        return NotificationMapper.response(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllRead(ObjectId userId) {
        notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false).forEach(notification -> {
            markRead(notification);
            notificationRepository.save(notification);
        });
    }

    public void markServerInviteRead(ObjectId userId, ObjectId serverInviteId) {
        notificationRepository.findByUserIdAndServerInviteId(userId, serverInviteId).ifPresent(notification -> {
            markRead(notification);
            notificationRepository.save(notification);
        });
    }

    private void markRead(NotificationDocument notification) {
        if (!notification.isRead) {
            notification.isRead = true;
            notification.readAt = Instant.now();
        }
    }
}
