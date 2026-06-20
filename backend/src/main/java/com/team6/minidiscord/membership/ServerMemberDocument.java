package com.team6.minidiscord.membership;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "server_members")
public class ServerMemberDocument {
    @Id
    public ObjectId id;
    public ObjectId serverId;
    public ObjectId userId;
    public MemberRole role;
    public Instant joinedAt;
    public Instant updatedAt;
}
