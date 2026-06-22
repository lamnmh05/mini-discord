package com.team6.minidiscord.common.mongo;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MongoIndexInitializer {
    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        index("users", new Index().on("usernameKey", Sort.Direction.ASC).unique());
        index("users", new Index().on("emailKey", Sort.Direction.ASC).unique());

        index("refresh_tokens", new Index().on("tokenHash", Sort.Direction.ASC).unique());
        index("refresh_tokens", new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("refresh_tokens", new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO));

        index("password_reset_tokens", new Index().on("tokenHash", Sort.Direction.ASC).unique());
        index("password_reset_tokens", new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("password_reset_tokens", new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO));

        index("servers", new Index().on("createdById", Sort.Direction.ASC));
        index("servers", new Index().on("deletedAt", Sort.Direction.ASC));

        index("server_members", new Index().on("serverId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC).unique());
        index("server_members", new Index().on("userId", Sort.Direction.ASC).on("joinedAt", Sort.Direction.DESC));
        index("server_members", new Index().on("serverId", Sort.Direction.ASC).on("role", Sort.Direction.ASC));

        index("channels", new Index().on("serverId", Sort.Direction.ASC).on("position", Sort.Direction.ASC));
        index("channels", new Index()
                .on("serverId", Sort.Direction.ASC)
                .on("nameKey", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("deletedAt").is(null))));

        index("messages", new Index().on("channelId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC));
        index("messages", new Index().on("conversationId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC));
        index("messages", new Index().on("serverId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC));
        index("messages", new Index().on("senderId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("messages", new Index().on("senderId", Sort.Direction.ASC).on("clientRequestId", Sort.Direction.ASC).unique().sparse());
        mongoTemplate.indexOps("messages").ensureIndex(new TextIndexDefinition.TextIndexDefinitionBuilder().onField("content").build());

        index("friendships", new Index().on("pairKey", Sort.Direction.ASC).unique());
        index("friendships", new Index().on("requesterId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("requestedAt", Sort.Direction.DESC));
        index("friendships", new Index().on("addresseeId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("requestedAt", Sort.Direction.DESC));

        index("direct_conversations", new Index().on("participantKey", Sort.Direction.ASC).unique());
        index("direct_conversations", new Index().on("participantIds", Sort.Direction.ASC).on("lastMessageAt", Sort.Direction.DESC));
        index("direct_participants", new Index().on("conversationId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC).unique());
        index("direct_participants", new Index().on("userId", Sort.Direction.ASC).on("updatedAt", Sort.Direction.DESC));

        index("invite_codes", new Index().on("code", Sort.Direction.ASC).unique());
        index("invite_codes", new Index().on("serverId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));

        index("server_invites", new Index().on("inviteeId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("server_invites", new Index()
                .on("serverId", Sort.Direction.ASC)
                .on("inviteeId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is("PENDING"))));

        index("notifications", new Index().on("userId", Sort.Direction.ASC).on("isRead", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("notifications", new Index().on("userId", Sort.Direction.ASC).on("serverInviteId", Sort.Direction.ASC));
    }

    private void index(String collection, Index index) {
        mongoTemplate.indexOps(collection).ensureIndex(index);
    }
}
