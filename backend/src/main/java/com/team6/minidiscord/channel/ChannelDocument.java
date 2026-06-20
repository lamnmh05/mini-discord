package com.team6.minidiscord.channel;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "channels")
public class ChannelDocument {
    @Id
    public ObjectId id;
    public ObjectId serverId;
    public ObjectId createdById;
    public String name;
    public String nameKey;
    public ChannelType type = ChannelType.TEXT;
    public int position;
    public Instant deletedAt;
    public Instant createdAt;
    public Instant updatedAt;
}
