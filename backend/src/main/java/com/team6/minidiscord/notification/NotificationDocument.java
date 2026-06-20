package com.team6.minidiscord.notification;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "notifications")
public class NotificationDocument {
    @Id
    public ObjectId id;
    public ObjectId userId;
    public ObjectId actorId;
    public ObjectId serverInviteId;
    public NotificationType type;
    public String title;
    public String body;
    public Map<String, Object> data = new HashMap<>();
    public boolean isRead;
    public Instant readAt;
    public Instant createdAt;
}
