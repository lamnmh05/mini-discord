package com.team6.minidiscord.friend;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends MongoRepository<FriendshipDocument, ObjectId> {
    Optional<FriendshipDocument> findByPairKey(String pairKey);

    List<FriendshipDocument> findByRequesterIdOrAddresseeId(ObjectId requesterId, ObjectId addresseeId);

    List<FriendshipDocument> findByAddresseeIdAndStatusOrderByRequestedAtDesc(ObjectId addresseeId, FriendStatus status);

    List<FriendshipDocument> findByRequesterIdAndStatusOrderByRequestedAtDesc(ObjectId requesterId, FriendStatus status);

    void deleteByPairKey(String pairKey);
}
