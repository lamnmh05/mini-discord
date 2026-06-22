package com.team6.minidiscord.direct;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DirectParticipantRepository extends MongoRepository<DirectParticipantDocument, ObjectId> {
    Optional<DirectParticipantDocument> findByConversationIdAndUserId(ObjectId conversationId, ObjectId userId);

    boolean existsByConversationIdAndUserId(ObjectId conversationId, ObjectId userId);

    List<DirectParticipantDocument> findByUserIdAndHiddenAtIsNullOrderByUpdatedAtDesc(ObjectId userId);

    List<DirectParticipantDocument> findByConversationId(ObjectId conversationId);
}
