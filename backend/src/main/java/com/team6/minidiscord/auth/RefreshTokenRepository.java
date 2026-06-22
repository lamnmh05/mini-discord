package com.team6.minidiscord.auth;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshTokenDocument, ObjectId> {
    Optional<RefreshTokenDocument> findByTokenHash(String tokenHash);

    List<RefreshTokenDocument> findByUserIdAndRevokedAtIsNull(ObjectId userId);
}
