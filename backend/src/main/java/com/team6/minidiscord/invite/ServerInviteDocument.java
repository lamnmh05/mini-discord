package com.team6.minidiscord.invite;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "server_invites")
public class ServerInviteDocument {
    @Id
    public ObjectId id;
    public ObjectId serverId;
    public ObjectId inviterId;
    public ObjectId inviteeId;
    public InviteStatus status;
    public Instant expiresAt;
    public Instant respondedAt;
    public Instant createdAt;
    public Instant updatedAt;
}
