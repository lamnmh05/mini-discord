package com.team6.minidiscord.message;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "messages")
public class MessageDocument {
    @Id
    public ObjectId id;
    public MessageScope scope = MessageScope.SERVER;
    public ObjectId serverId;
    public ObjectId channelId;
    public ObjectId conversationId;
    public ObjectId senderId;
    public String content;
    public MessageType messageType;
    public SenderSnapshot senderSnapshot;
    public List<Attachment> attachments = new ArrayList<>();
    public List<Reaction> reactions = new ArrayList<>();
    public String clientRequestId;
    public Instant editedAt;
    public Instant deletedAt;
    public ObjectId deletedById;
    public Instant createdAt;
    public Instant updatedAt;
}
