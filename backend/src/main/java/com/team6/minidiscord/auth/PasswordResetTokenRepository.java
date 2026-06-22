package com.team6.minidiscord.auth;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetTokenDocument, ObjectId> {
    Optional<PasswordResetTokenDocument> findByTokenHash(String tokenHash);
}
