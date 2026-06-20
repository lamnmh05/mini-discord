package com.team6.minidiscord.channel;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends MongoRepository<ChannelDocument, ObjectId> {
    Optional<ChannelDocument> findByIdAndDeletedAtIsNull(ObjectId id);

    List<ChannelDocument> findByServerIdAndDeletedAtIsNullOrderByPositionAsc(ObjectId serverId);

    Optional<ChannelDocument> findTopByServerIdAndDeletedAtIsNullOrderByPositionDesc(ObjectId serverId);

    boolean existsByServerIdAndNameKeyAndDeletedAtIsNull(ObjectId serverId, String nameKey);
}
