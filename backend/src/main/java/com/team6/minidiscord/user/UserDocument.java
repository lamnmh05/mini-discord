package com.team6.minidiscord.user;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
public class UserDocument {
    @Id
    public ObjectId id;
    public String username;
    public String usernameKey;
    public String email;
    public String emailKey;
    public String passwordHash;
    public String displayName;
    public String avatarUrl;
    public String customStatus;
    public AccountStatus accountStatus = AccountStatus.ACTIVE;
    public Instant lastSeenAt;
    public Instant createdAt;
    public Instant updatedAt;
}
