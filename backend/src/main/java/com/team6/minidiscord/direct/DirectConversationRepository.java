package com.team6.minidiscord.direct;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DirectConversationRepository extends MongoRepository<DirectConversationDocument, ObjectId> {
    Optional<DirectConversationDocument> findByIdAndDeletedAtIsNull(ObjectId id);

    Optional<DirectConversationDocument> findByParticipantKeyAndDeletedAtIsNull(String participantKey);

    List<DirectConversationDocument> findByIdInAndDeletedAtIsNull(Collection<ObjectId> ids);
}
