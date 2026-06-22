package com.team6.minidiscord.message;

import com.team6.minidiscord.channel.ChannelRepository;
import com.team6.minidiscord.common.api.CursorPage;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.message.dto.MessageResponse;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class MessageQueryService {
    private final MongoTemplate mongoTemplate;
    private final ChannelRepository channelRepository;
    private final MembershipService membershipService;
    private final MessageAccessPolicy accessPolicy;

    public MessageQueryService(
            MongoTemplate mongoTemplate,
            ChannelRepository channelRepository,
            MembershipService membershipService,
            MessageAccessPolicy accessPolicy
    ) {
        this.mongoTemplate = mongoTemplate;
        this.channelRepository = channelRepository;
        this.membershipService = membershipService;
        this.accessPolicy = accessPolicy;
    }

    public CursorPage<MessageResponse> channelHistory(ObjectId userId, String channelIdValue, String cursor, Integer limit) {
        ObjectId channelId = ObjectIds.parse(channelIdValue);
        accessPolicy.requireServerChannel(userId, channelId);
        int pageSize = clamp(limit);
        Query query = new Query(Criteria.where("channelId").is(channelId).and("deletedAt").is(null));
        applyCursor(query, cursor);
        applyMessageSort(query);
        query.limit(pageSize + 1);
        return page(mongoTemplate.find(query, MessageDocument.class), pageSize);
    }

    public CursorPage<MessageResponse> directHistory(ObjectId userId, String conversationIdValue, String cursor, Integer limit) {
        ObjectId conversationId = ObjectIds.parse(conversationIdValue);
        accessPolicy.requireDirectConversation(userId, conversationId);
        int pageSize = clamp(limit);
        Query query = new Query(Criteria.where("conversationId").is(conversationId).and("deletedAt").is(null));
        applyCursor(query, cursor);
        applyMessageSort(query);
        query.limit(pageSize + 1);
        return page(mongoTemplate.find(query, MessageDocument.class), pageSize);
    }

    public CursorPage<MessageResponse> search(ObjectId userId, String serverIdValue, String q, String channelIdValue, String cursor, Integer limit) {
        if (q == null || q.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Tu khoa tim kiem khong duoc rong.");
        }
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireMember(serverId, userId);
        List<ObjectId> activeChannelIds = channelRepository.findByServerIdAndDeletedAtIsNullOrderByPositionAsc(serverId).stream()
                .map(channel -> channel.id)
                .toList();
        if (activeChannelIds.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }
        ObjectId explicitChannelId = null;
        if (channelIdValue != null && !channelIdValue.isBlank()) {
            ObjectId channelId = ObjectIds.parse(channelIdValue);
            if (!activeChannelIds.contains(channelId)) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Channel khong ton tai.");
            }
            explicitChannelId = channelId;
        }
        Criteria criteria = Criteria.where("serverId").is(serverId)
                .and("channelId").in(explicitChannelId == null ? activeChannelIds : List.of(explicitChannelId))
                .and("deletedAt").is(null);
        Query query = TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(q)).addCriteria(criteria);
        applyCursor(query, cursor);
        applyMessageSort(query);
        int pageSize = clamp(limit);
        query.limit(pageSize + 1);
        return page(mongoTemplate.find(query, MessageDocument.class), pageSize);
    }

    private void applyMessageSort(Query query) {
        query.with(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id")));
    }

    private int clamp(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private void applyCursor(Query query, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return;
        }
        String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");
        if (parts.length != 2 || !ObjectId.isValid(parts[1])) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cursor khong hop le.");
        }
        Instant createdAt = Instant.parse(parts[0]);
        ObjectId id = new ObjectId(parts[1]);
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("createdAt").lt(createdAt),
                Criteria.where("createdAt").is(createdAt).and("_id").lt(id)
        ));
    }

    private CursorPage<MessageResponse> page(List<MessageDocument> docs, int pageSize) {
        boolean hasNext = docs.size() > pageSize;
        List<MessageDocument> slice = hasNext ? docs.subList(0, pageSize) : docs;
        String nextCursor = null;
        if (hasNext && !slice.isEmpty()) {
            MessageDocument last = slice.get(slice.size() - 1);
            nextCursor = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((last.createdAt + "|" + last.id.toHexString()).getBytes(StandardCharsets.UTF_8));
        }
        return new CursorPage<>(slice.stream().map(MessageMapper::response).toList(), nextCursor);
    }
}
