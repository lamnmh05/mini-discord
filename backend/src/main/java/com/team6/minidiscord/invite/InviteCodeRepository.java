package com.team6.minidiscord.invite;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InviteCodeRepository extends MongoRepository<InviteCodeDocument, ObjectId> {
    Optional<InviteCodeDocument> findByCode(String code);

    List<InviteCodeDocument> findByServerIdOrderByCreatedAtDesc(ObjectId serverId);
}
