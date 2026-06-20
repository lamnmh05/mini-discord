package com.team6.minidiscord.server;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "servers")
public class ServerDocument {
    @Id
    public ObjectId id;
    public String name;
    public String iconUrl;
    public ObjectId createdById;
    public ObjectId defaultChannelId;
    public Instant deletedAt;
    public Instant createdAt;
    public Instant updatedAt;
}
