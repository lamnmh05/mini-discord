package com.team6.minidiscord.user;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<UserDocument, ObjectId> {
    Optional<UserDocument> findByEmailKey(String emailKey);

    Optional<UserDocument> findByUsernameKey(String usernameKey);

    boolean existsByEmailKey(String emailKey);

    boolean existsByUsernameKey(String usernameKey);

    List<UserDocument> findByIdIn(Collection<ObjectId> ids);
}
