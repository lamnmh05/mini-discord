package com.team6.minidiscord.server;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServerRepository extends MongoRepository<ServerDocument, ObjectId> {
    Optional<ServerDocument> findByIdAndDeletedAtIsNull(ObjectId id);

    List<ServerDocument> findByIdInAndDeletedAtIsNull(Collection<ObjectId> ids);
}
