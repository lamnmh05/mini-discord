package com.team6.minidiscord.direct;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "direct_conversations")
public class DirectConversationDocument {
    @Id
    public ObjectId id;
    public List<ObjectId> participantIds = new ArrayList<>();
    public String participantKey;
    public ObjectId lastMessageId;
    public Instant lastMessageAt;
    public String lastMessagePreview;
    public ObjectId createdById;
    public Instant deletedAt;
    public Instant createdAt;
    public Instant updatedAt;
}
