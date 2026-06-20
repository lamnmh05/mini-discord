package com.team6.minidiscord.notification;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<NotificationDocument, ObjectId> {
    Optional<NotificationDocument> findByIdAndUserId(ObjectId id, ObjectId userId);

    Optional<NotificationDocument> findByUserIdAndServerInviteId(ObjectId userId, ObjectId serverInviteId);

    List<NotificationDocument> findByUserIdOrderByCreatedAtDesc(ObjectId userId);

    List<NotificationDocument> findByUserIdAndIsReadOrderByCreatedAtDesc(ObjectId userId, boolean isRead);
}
