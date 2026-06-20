package com.team6.minidiscord.membership;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServerMemberRepository extends MongoRepository<ServerMemberDocument, ObjectId> {
    Optional<ServerMemberDocument> findByServerIdAndUserId(ObjectId serverId, ObjectId userId);

    boolean existsByServerIdAndUserId(ObjectId serverId, ObjectId userId);

    long countByServerIdAndRole(ObjectId serverId, MemberRole role);

    List<ServerMemberDocument> findByUserIdOrderByJoinedAtDesc(ObjectId userId);

    List<ServerMemberDocument> findByServerId(ObjectId serverId);

    List<ServerMemberDocument> findByServerIdAndUserIdIn(ObjectId serverId, Collection<ObjectId> userIds);

    void deleteByServerIdAndUserId(ObjectId serverId, ObjectId userId);
}
