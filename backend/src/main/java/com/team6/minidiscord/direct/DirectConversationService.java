package com.team6.minidiscord.direct;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.direct.dto.DirectConversationResponse;
import com.team6.minidiscord.friend.FriendshipService;
import com.team6.minidiscord.friend.dto.FriendUserResponse;
import com.team6.minidiscord.message.MessageDocument;
import com.team6.minidiscord.realtime.PresenceLookupService;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DirectConversationService {
    private final DirectConversationRepository conversationRepository;
    private final DirectParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final FriendshipService friendshipService;
    private final PresenceLookupService presenceLookupService;
    private final MongoTemplate mongoTemplate;

    public DirectConversationService(
            DirectConversationRepository conversationRepository,
            DirectParticipantRepository participantRepository,
            UserRepository userRepository,
            FriendshipService friendshipService,
            PresenceLookupService presenceLookupService,
            MongoTemplate mongoTemplate
    ) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.friendshipService = friendshipService;
        this.presenceLookupService = presenceLookupService;
        this.mongoTemplate = mongoTemplate;
    }

    public List<DirectConversationResponse> list(ObjectId userId) {
        List<DirectParticipantDocument> participantRows = participantRepository.findByUserIdAndHiddenAtIsNullOrderByUpdatedAtDesc(userId);
        if (participantRows.isEmpty()) {
            return List.of();
        }
        Map<ObjectId, DirectParticipantDocument> participantByConversationId = participantRows.stream()
                .collect(Collectors.toMap(participant -> participant.conversationId, Function.identity(), (left, right) -> left));
        Map<ObjectId, DirectConversationDocument> conversations = conversationRepository
                .findByIdInAndDeletedAtIsNull(participantByConversationId.keySet())
                .stream()
                .collect(Collectors.toMap(conversation -> conversation.id, Function.identity()));
        List<ObjectId> otherUserIds = conversations.values().stream()
                .map(conversation -> otherParticipantId(conversation, userId))
                .toList();
        Map<ObjectId, UserDocument> usersById = userRepository.findByIdIn(otherUserIds).stream()
                .collect(Collectors.toMap(user -> user.id, Function.identity()));

        return participantRows.stream()
                .map(participant -> {
                    DirectConversationDocument conversation = conversations.get(participant.conversationId);
                    if (conversation == null) {
                        return null;
                    }
                    UserDocument recipient = usersById.get(otherParticipantId(conversation, userId));
                    if (recipient == null) {
                        return null;
                    }
                    return response(conversation, participant, recipient, userId);
                })
                .filter(response -> response != null)
                .sorted(Comparator.comparing(
                        DirectConversationResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    @Transactional
    public DirectConversationResponse open(ObjectId userId, String recipientIdValue) {
        ObjectId recipientId = ObjectIds.parse(recipientIdValue);
        if (recipientId.equals(userId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "You cannot create a direct conversation with yourself.");
        }
        friendshipService.requireFriends(userId, recipientId);
        UserDocument recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
        Instant now = Instant.now();
        String participantKey = FriendshipService.pairKey(userId, recipientId);
        DirectConversationDocument conversation = conversationRepository.findByParticipantKeyAndDeletedAtIsNull(participantKey).orElse(null);
        if (conversation == null) {
            conversation = new DirectConversationDocument();
            conversation.participantKey = participantKey;
            conversation.participantIds = sortedParticipants(userId, recipientId);
            conversation.createdById = userId;
            conversation.createdAt = now;
            conversation.updatedAt = now;
            conversation = conversationRepository.save(conversation);
            createParticipant(conversation.id, userId, now);
            createParticipant(conversation.id, recipientId, now);
        }
        DirectParticipantDocument participant = requireParticipant(conversation.id, userId);
        if (participant.hiddenAt != null) {
            participant.hiddenAt = null;
            participant.updatedAt = now;
            participantRepository.save(participant);
        }
        return response(conversation, participant, recipient, userId);
    }

    public DirectParticipantDocument requireParticipant(ObjectId conversationId, ObjectId userId) {
        conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Direct conversation not found."));
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "You are not a participant in this conversation."));
    }

    public boolean isParticipant(ObjectId conversationId, ObjectId userId) {
        return conversationRepository.findByIdAndDeletedAtIsNull(conversationId).isPresent()
                && participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    public void requireCanMessage(ObjectId conversationId, ObjectId userId) {
        DirectConversationDocument conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Direct conversation not found."));
        requireParticipant(conversationId, userId);
        friendshipService.requireFriends(userId, otherParticipantId(conversation, userId));
    }

    @Transactional
    public void recordMessage(MessageDocument message) {
        DirectConversationDocument conversation = conversationRepository.findByIdAndDeletedAtIsNull(message.conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Direct conversation not found."));
        conversation.lastMessageId = message.id;
        conversation.lastMessageAt = message.createdAt;
        conversation.lastMessagePreview = preview(message);
        conversation.updatedAt = message.createdAt;
        conversationRepository.save(conversation);
        participantRepository.findByConversationId(conversation.id).forEach(participant -> {
            participant.hiddenAt = null;
            participant.updatedAt = message.createdAt;
            participantRepository.save(participant);
        });
    }

    @Transactional
    public DirectConversationResponse markRead(ObjectId userId, String conversationIdValue) {
        ObjectId conversationId = ObjectIds.parse(conversationIdValue);
        DirectParticipantDocument participant = requireParticipant(conversationId, userId);
        DirectConversationDocument conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Direct conversation not found."));
        MessageDocument latest = latestMessage(conversationId);
        Instant now = Instant.now();
        participant.lastReadMessageId = latest == null ? null : latest.id;
        participant.lastReadAt = latest == null ? now : latest.createdAt;
        participant.updatedAt = now;
        participantRepository.save(participant);
        UserDocument recipient = userRepository.findById(otherParticipantId(conversation, userId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
        return response(conversation, participant, recipient, userId);
    }

    @Transactional
    public void refreshLastMessage(ObjectId conversationId) {
        DirectConversationDocument conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId).orElse(null);
        if (conversation == null) {
            return;
        }
        MessageDocument latest = latestMessage(conversationId);
        conversation.lastMessageId = latest == null ? null : latest.id;
        conversation.lastMessageAt = latest == null ? null : latest.createdAt;
        conversation.lastMessagePreview = latest == null ? null : preview(latest);
        conversation.updatedAt = Instant.now();
        conversationRepository.save(conversation);
    }

    private DirectConversationResponse response(
            DirectConversationDocument conversation,
            DirectParticipantDocument participant,
            UserDocument recipient,
            ObjectId currentUserId
    ) {
        return new DirectConversationResponse(
                conversation.id.toHexString(),
                userResponse(recipient),
                conversation.lastMessagePreview,
                conversation.lastMessageAt,
                unreadCount(conversation.id, currentUserId, participant.lastReadAt),
                conversation.createdAt,
                conversation.updatedAt
        );
    }

    private long unreadCount(ObjectId conversationId, ObjectId userId, Instant lastReadAt) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId)
                .and("senderId").ne(userId)
                .and("deletedAt").is(null);
        if (lastReadAt != null) {
            criteria = criteria.and("createdAt").gt(lastReadAt);
        }
        return mongoTemplate.count(new Query(criteria), MessageDocument.class);
    }

    private MessageDocument latestMessage(ObjectId conversationId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId).and("deletedAt").is(null));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id")));
        query.limit(1);
        return mongoTemplate.findOne(query, MessageDocument.class);
    }

    private DirectParticipantDocument createParticipant(ObjectId conversationId, ObjectId userId, Instant now) {
        DirectParticipantDocument participant = new DirectParticipantDocument();
        participant.conversationId = conversationId;
        participant.userId = userId;
        participant.joinedAt = now;
        participant.updatedAt = now;
        return participantRepository.save(participant);
    }

    private List<ObjectId> sortedParticipants(ObjectId left, ObjectId right) {
        return left.toHexString().compareTo(right.toHexString()) <= 0 ? List.of(left, right) : List.of(right, left);
    }

    private ObjectId otherParticipantId(DirectConversationDocument conversation, ObjectId userId) {
        return conversation.participantIds.stream()
                .filter(participantId -> !participantId.equals(userId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_INTEGRITY_ERROR, "Direct conversation has no recipient."));
    }

    private FriendUserResponse userResponse(UserDocument user) {
        return new FriendUserResponse(
                user.id.toHexString(),
                user.username,
                user.displayName,
                user.avatarUrl,
                user.customStatus,
                user.lastSeenAt,
                presenceLookupService.status(user.id)
        );
    }

    private String preview(MessageDocument message) {
        if (message.content != null && !message.content.isBlank()) {
            return message.content.length() <= 120 ? message.content : message.content.substring(0, 120);
        }
        int attachmentCount = message.attachments == null ? 0 : message.attachments.size();
        return attachmentCount == 0 ? "" : attachmentCount + " attachment" + (attachmentCount == 1 ? "" : "s");
    }
}
