package com.team6.minidiscord.invite;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "invite_codes")
public class InviteCodeDocument {
    @Id
    public ObjectId id;
    public ObjectId serverId;
    public ObjectId createdById;
    public String code;
    public Integer maxUses;
    public int useCount;
    public Instant expiresAt;
    public Instant revokedAt;
    public Instant createdAt;
}
