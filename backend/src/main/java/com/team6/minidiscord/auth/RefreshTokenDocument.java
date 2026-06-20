package com.team6.minidiscord.auth;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "refresh_tokens")
public class RefreshTokenDocument {
    @Id
    public ObjectId id;
    public ObjectId userId;
    public String tokenHash;
    public String deviceInfo;
    public String ipAddress;
    public Instant expiresAt;
    public Instant revokedAt;
    public ObjectId replacedByTokenId;
    public Instant createdAt;
}
