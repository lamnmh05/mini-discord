package com.team6.minidiscord.auth;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "password_reset_tokens")
public class PasswordResetTokenDocument {
    @Id
    public ObjectId id;
    public ObjectId userId;
    public String tokenHash;
    public String ipAddress;
    public Instant expiresAt;
    public Instant usedAt;
    public Instant createdAt;
}
