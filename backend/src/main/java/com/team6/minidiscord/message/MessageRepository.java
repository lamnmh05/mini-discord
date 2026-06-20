package com.team6.minidiscord.message;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MessageRepository extends MongoRepository<MessageDocument, ObjectId> {
    Optional<MessageDocument> findByIdAndDeletedAtIsNull(ObjectId id);

    Optional<MessageDocument> findBySenderIdAndClientRequestId(ObjectId senderId, String clientRequestId);
}
