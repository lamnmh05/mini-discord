package com.team6.minidiscord.message;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelRepository;
import com.team6.minidiscord.channel.ChannelService;
import com.team6.minidiscord.common.api.CursorPage;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.file.FileStorageService;
import com.team6.minidiscord.membership.MemberRole;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.membership.ServerMemberDocument;
import com.team6.minidiscord.message.dto.CreateMessageRequest;
import com.team6.minidiscord.message.dto.EditMessageRequest;
import com.team6.minidiscord.message.dto.MessageResponse;
import com.team6.minidiscord.realtime.WebSocketEvent;
import com.team6.minidiscord.realtime.WebSocketEventPublisher;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserService;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final ChannelService channelService;
    private final ChannelRepository channelRepository;
    private final MembershipService membershipService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final WebSocketEventPublisher publisher;

    public MessageService(
            MessageRepository messageRepository,
            MongoTemplate mongoTemplate,
            ChannelService channelService,
            ChannelRepository channelRepository,
            MembershipService membershipService,
            UserService userService,
            FileStorageService fileStorageService,
            WebSocketEventPublisher publisher
    ) {
        this.messageRepository = messageRepository;
        this.mongoTemplate = mongoTemplate;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.membershipService = membershipService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.publisher = publisher;
    }

    public CursorPage<MessageResponse> history(ObjectId userId, String channelIdValue, String cursor, Integer limit) {
        ObjectId channelId = ObjectIds.parse(channelIdValue);
        ChannelDocument channel = channelService.requireActiveChannel(channelId);
        membershipService.requireMember(channel.serverId, userId);
        int pageSize = clamp(limit);
        Query query = new Query(baseChannelCriteria(channelId));
        applyCursor(query, cursor);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id")));
        query.limit(pageSize + 1);
        List<MessageDocument> docs = mongoTemplate.find(query, MessageDocument.class);
        return page(docs, pageSize);
    }

    @Transactional
    public MessageResponse send(ObjectId userId, String channelIdValue, CreateMessageRequest request) {
        ObjectId channelId = ObjectIds.parse(channelIdValue);
        ChannelDocument channel = channelService.requireActiveChannel(channelId);
        membershipService.requireMember(channel.serverId, userId);
        String normalizedContent = request.content() == null ? null : request.content().trim();
        List<Attachment> attachments = MessageMapper.attachmentsFromInputs(request.attachments());
        if ((normalizedContent == null || normalizedContent.isBlank()) && attachments.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Message cần content hoặc attachment.");
        }
        if (request.clientRequestId() != null && !request.clientRequestId().isBlank()) {
            var existing = messageRepository.findBySenderIdAndClientRequestId(userId, request.clientRequestId());
            if (existing.isPresent()) {
                return MessageMapper.response(existing.get());
            }
        }
        fileStorageService.verifyOwnership(userId, attachments);
        UserDocument sender = userService.getActiveUser(userId);
        Instant now = Instant.now();
        MessageDocument message = new MessageDocument();
        message.serverId = channel.serverId;
        message.channelId = channel.id;
        message.senderId = userId;
        message.content = normalizedContent;
        message.messageType = messageType(normalizedContent, attachments);
        message.senderSnapshot = snapshot(sender);
        message.attachments = attachments;
        message.reactions = new ArrayList<>();
        message.clientRequestId = request.clientRequestId();
        message.createdAt = now;
        message.updatedAt = now;
        message = messageRepository.save(message);
        fileStorageService.consumeOwnership(userId, attachments);
        MessageResponse response = MessageMapper.response(message);
        publisher.channelEvent(channel.id.toHexString(), WebSocketEvent.of(
                "MESSAGE_CREATED",
                channel.serverId.toHexString(),
                channel.id.toHexString(),
                response
        ));
        return response;
    }

    @Transactional
    public MessageResponse edit(ObjectId userId, String messageIdValue, EditMessageRequest request) {
        MessageDocument message = requireActiveMessage(messageIdValue);
        channelService.requireActiveChannel(message.channelId);
        membershipService.requireMember(message.serverId, userId);
        if (!message.senderId.equals(userId)) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Bạn chỉ được sửa message của chính mình.");
        }
        message.content = request.content().trim();
        message.editedAt = Instant.now();
        message.updatedAt = message.editedAt;
        message = messageRepository.save(message);
        MessageResponse response = MessageMapper.response(message);
        publisher.channelEvent(message.channelId.toHexString(), WebSocketEvent.of(
                "MESSAGE_UPDATED",
                message.serverId.toHexString(),
                message.channelId.toHexString(),
                response
        ));
        return response;
    }

    @Transactional
    public void softDelete(ObjectId userId, String messageIdValue) {
        MessageDocument message = requireActiveMessage(messageIdValue);
        channelService.requireActiveChannel(message.channelId);
        ServerMemberDocument member = membershipService.requireMember(message.serverId, userId);
        if (!message.senderId.equals(userId) && member.role != MemberRole.OWNER) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Bạn không có quyền xóa message này.");
        }
        Instant now = Instant.now();
        message.deletedAt = now;
        message.deletedById = userId;
        message.updatedAt = now;
        messageRepository.save(message);
        publisher.channelEvent(message.channelId.toHexString(), WebSocketEvent.of(
                "MESSAGE_DELETED",
                message.serverId.toHexString(),
                message.channelId.toHexString(),
                java.util.Map.of("messageId", message.id.toHexString(), "deletedById", userId.toHexString())
        ));
    }

    @Transactional
    public MessageResponse addReaction(ObjectId userId, String messageIdValue, String emoji) {
        MessageDocument message = requireReactableMessage(userId, messageIdValue, emoji);
        Reaction reaction = message.reactions.stream()
                .filter(r -> r.emoji.equals(emoji))
                .findFirst()
                .orElse(null);
        if (reaction == null) {
            reaction = new Reaction();
            reaction.emoji = emoji;
            message.reactions.add(reaction);
        }
        if (!reaction.userIds.contains(userId)) {
            reaction.userIds.add(userId);
        }
        message.updatedAt = Instant.now();
        message = messageRepository.save(message);
        publishReaction(message);
        return MessageMapper.response(message);
    }

    @Transactional
    public MessageResponse removeReaction(ObjectId userId, String messageIdValue, String emoji) {
        MessageDocument message = requireReactableMessage(userId, messageIdValue, emoji);
        message.reactions.forEach(r -> {
            if (r.emoji.equals(emoji)) {
                r.userIds.remove(userId);
            }
        });
        message.reactions.removeIf(r -> r.userIds.isEmpty());
        message.updatedAt = Instant.now();
        message = messageRepository.save(message);
        publishReaction(message);
        return MessageMapper.response(message);
    }

    public CursorPage<MessageResponse> search(ObjectId userId, String serverIdValue, String q, String channelIdValue, String cursor, Integer limit) {
        if (q == null || q.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Từ khóa tìm kiếm không được rỗng.");
        }
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireMember(serverId, userId);
        List<ObjectId> activeChannelIds = channelRepository.findByServerIdAndDeletedAtIsNullOrderByPositionAsc(serverId).stream()
                .map(ch -> ch.id)
                .toList();
        if (activeChannelIds.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }
        ObjectId explicitChannelId = null;
        if (channelIdValue != null && !channelIdValue.isBlank()) {
            ObjectId channelId = ObjectIds.parse(channelIdValue);
            if (!activeChannelIds.contains(channelId)) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Channel không tồn tại.");
            }
            explicitChannelId = channelId;
        }
        Criteria criteria = Criteria.where("serverId").is(serverId)
                .and("channelId").in(explicitChannelId == null ? activeChannelIds : List.of(explicitChannelId))
                .and("deletedAt").is(null);
        Query query = TextQueryCompat.queryText(q).addCriteria(criteria);
        applyCursor(query, cursor);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id")));
        query.limit(clamp(limit) + 1);
        return page(mongoTemplate.find(query, MessageDocument.class), clamp(limit));
    }

    private MessageDocument requireActiveMessage(String messageIdValue) {
        return messageRepository.findByIdAndDeletedAtIsNull(ObjectIds.parse(messageIdValue))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Message không tồn tại."));
    }

    private MessageDocument requireReactableMessage(ObjectId userId, String messageIdValue, String emoji) {
        if (emoji == null || emoji.isBlank() || emoji.length() > 32) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Emoji không hợp lệ.");
        }
        MessageDocument message = requireActiveMessage(messageIdValue);
        channelService.requireActiveChannel(message.channelId);
        membershipService.requireMember(message.serverId, userId);
        return message;
    }

    private Criteria baseChannelCriteria(ObjectId channelId) {
        return Criteria.where("channelId").is(channelId).and("deletedAt").is(null);
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
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cursor không hợp lệ.");
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

    private MessageType messageType(String content, List<Attachment> attachments) {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasFile = attachments != null && !attachments.isEmpty();
        if (hasContent && hasFile) {
            return MessageType.MIXED;
        }
        return hasFile ? MessageType.FILE : MessageType.TEXT;
    }

    private SenderSnapshot snapshot(UserDocument user) {
        SenderSnapshot snapshot = new SenderSnapshot();
        snapshot.username = user.username;
        snapshot.displayName = user.displayName;
        snapshot.avatarUrl = user.avatarUrl;
        return snapshot;
    }

    private void publishReaction(MessageDocument message) {
        MessageResponse response = MessageMapper.response(message);
        publisher.channelEvent(message.channelId.toHexString(), WebSocketEvent.of(
                "REACTION_UPDATED",
                message.serverId.toHexString(),
                message.channelId.toHexString(),
                response
        ));
    }

    private static final class TextQueryCompat {
        private static Query queryText(String q) {
            return TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(q));
        }
    }
}
