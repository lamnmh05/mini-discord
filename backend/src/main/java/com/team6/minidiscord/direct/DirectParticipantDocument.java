package com.team6.minidiscord.direct;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "direct_participants")
public class DirectParticipantDocument {
    @Id
    public ObjectId id;
    public ObjectId conversationId;
    public ObjectId userId;
    public ObjectId lastReadMessageId;
    public Instant lastReadAt;
    public Instant hiddenAt;
    public Instant joinedAt;
    public Instant updatedAt;
}
