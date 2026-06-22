package com.team6.minidiscord.friend;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "friendships")
public class FriendshipDocument {
    @Id
    public ObjectId id;
    public ObjectId requesterId;
    public ObjectId addresseeId;
    public String pairKey;
    public FriendStatus status;
    public Instant requestedAt;
    public Instant respondedAt;
    public Instant updatedAt;
}
