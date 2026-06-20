package com.team6.minidiscord.invite;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ServerInviteRepository extends MongoRepository<ServerInviteDocument, ObjectId> {
    Optional<ServerInviteDocument> findByIdAndInviteeId(ObjectId id, ObjectId inviteeId);

    Optional<ServerInviteDocument> findByServerIdAndInviteeIdAndStatus(ObjectId serverId, ObjectId inviteeId, InviteStatus status);

    List<ServerInviteDocument> findByInviteeIdAndStatusOrderByCreatedAtDesc(ObjectId inviteeId, InviteStatus status);
}
