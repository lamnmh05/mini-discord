package com.team6.minidiscord.message;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.direct.DirectConversationService;
import com.team6.minidiscord.file.FileStorageService;
import com.team6.minidiscord.message.dto.CreateMessageRequest;
import com.team6.minidiscord.message.dto.EditMessageRequest;
import com.team6.minidiscord.message.dto.MessageResponse;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserService;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MessageCommandService {
    private final MessageRepository messageRepository;
    private final MessageAccessPolicy accessPolicy;
    private final DirectConversationService directConversationService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final MessageRealtimePublisher realtimePublisher;

    public MessageCommandService(
            MessageRepository messageRepository,
            MessageAccessPolicy accessPolicy,
            DirectConversationService directConversationService,
            UserService userService,
            FileStorageService fileStorageService,
            MessageRealtimePublisher realtimePublisher
    ) {
        this.messageRepository = messageRepository;
        this.accessPolicy = accessPolicy;
        this.directConversationService = directConversationService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public MessageResponse sendToChannel(ObjectId userId, String channelIdValue, CreateMessageRequest request) {
        ObjectId channelId = ObjectIds.parse(channelIdValue);
        ChannelDocument channel = accessPolicy.requireServerChannel(userId, channelId);
        MessageDocument existing = existingIdempotentMessage(userId, request);
        if (existing != null) {
            return MessageMapper.response(existing);
        }
        MessageDocument message = buildMessage(userId, MessageScope.SERVER, channel.serverId, channel.id, null, request);
        message = saveNewMessage(userId, message);
        MessageResponse response = MessageMapper.response(message);
        realtimePublisher.created(message, response);
        return response;
    }

    @Transactional
    public MessageResponse sendDirect(ObjectId userId, String conversationIdValue, CreateMessageRequest request) {
        ObjectId conversationId = ObjectIds.parse(conversationIdValue);
        accessPolicy.requireCanSendDirect(userId, conversationId);
        MessageDocument existing = existingIdempotentMessage(userId, request);
        if (existing != null) {
            return MessageMapper.response(existing);
        }
        MessageDocument message = buildMessage(userId, MessageScope.DIRECT, null, null, conversationId, request);
        message = saveNewMessage(userId, message);
        directConversationService.recordMessage(message);
        MessageResponse response = MessageMapper.response(message);
        realtimePublisher.created(message, response);
        return response;
    }

    @Transactional
    public MessageResponse edit(ObjectId userId, String messageIdValue, EditMessageRequest request) {
        MessageDocument message = requireActiveMessage(messageIdValue);
        accessPolicy.requireVisible(userId, message);
        if (!message.senderId.equals(userId)) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Ban chi duoc sua message cua chinh minh.");
        }
        String newContent = request.content().trim();
        if (newContent.equals(message.content)) {
            return MessageMapper.response(message);
        }
        message.content = newContent;
        message.editedAt = Instant.now();
        message.updatedAt = message.editedAt;
        message = messageRepository.save(message);
        MessageResponse response = MessageMapper.response(message);
        realtimePublisher.updated(message, response);
        return response;
    }

    @Transactional
    public void softDelete(ObjectId userId, String messageIdValue) {
        MessageDocument message = requireActiveMessage(messageIdValue);
        accessPolicy.requireDeletable(userId, message);
        Instant now = Instant.now();
        message.deletedAt = now;
        message.deletedById = userId;
        message.updatedAt = now;
        messageRepository.save(message);
        if (accessPolicy.scope(message) == MessageScope.DIRECT) {
            directConversationService.refreshLastMessage(message.conversationId);
        }
        realtimePublisher.deleted(message, Map.of("messageId", message.id.toHexString(), "deletedById", userId.toHexString()));
    }

    @Transactional
    public MessageResponse addReaction(ObjectId userId, String messageIdValue, String emoji) {
        MessageDocument message = requireReactableMessage(userId, messageIdValue, emoji);
        Reaction reaction = message.reactions.stream()
                .filter(candidate -> candidate.emoji.equals(emoji))
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
        MessageResponse response = MessageMapper.response(message);
        realtimePublisher.reactionUpdated(message, response);
        return response;
    }

    @Transactional
    public MessageResponse removeReaction(ObjectId userId, String messageIdValue, String emoji) {
        MessageDocument message = requireReactableMessage(userId, messageIdValue, emoji);
        message.reactions.forEach(reaction -> {
            if (reaction.emoji.equals(emoji)) {
                reaction.userIds.remove(userId);
            }
        });
        message.reactions.removeIf(reaction -> reaction.userIds.isEmpty());
        message.updatedAt = Instant.now();
        message = messageRepository.save(message);
        MessageResponse response = MessageMapper.response(message);
        realtimePublisher.reactionUpdated(message, response);
        return response;
    }

    private MessageDocument buildMessage(
            ObjectId userId,
            MessageScope scope,
            ObjectId serverId,
            ObjectId channelId,
            ObjectId conversationId,
            CreateMessageRequest request
    ) {
        String normalizedContent = request.content() == null ? null : request.content().trim();
        List<Attachment> attachments = MessageMapper.attachmentsFromInputs(request.attachments());
        if ((normalizedContent == null || normalizedContent.isBlank()) && attachments.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Message needs content or attachment.");
        }
        UserDocument sender = userService.getActiveUser(userId);
        Instant now = Instant.now();
        MessageDocument message = new MessageDocument();
        message.scope = scope;
        message.serverId = serverId;
        message.channelId = channelId;
        message.conversationId = conversationId;
        message.senderId = userId;
        message.content = normalizedContent;
        message.messageType = messageType(normalizedContent, attachments);
        message.senderSnapshot = snapshot(sender);
        message.attachments = attachments;
        message.reactions = new ArrayList<>();
        message.clientRequestId = request.clientRequestId();
        message.createdAt = now;
        message.updatedAt = now;
        return message;
    }

    private MessageDocument existingIdempotentMessage(ObjectId userId, CreateMessageRequest request) {
        if (request.clientRequestId() == null || request.clientRequestId().isBlank()) {
            return null;
        }
        return messageRepository.findBySenderIdAndClientRequestId(userId, request.clientRequestId()).orElse(null);
    }

    private MessageDocument saveNewMessage(ObjectId userId, MessageDocument message) {
        if (message.id != null) {
            return message;
        }
        fileStorageService.verifyOwnership(userId, message.attachments);
        MessageDocument saved = messageRepository.save(message);
        fileStorageService.consumeOwnership(userId, message.attachments);
        return saved;
    }

    private MessageDocument requireActiveMessage(String messageIdValue) {
        return messageRepository.findByIdAndDeletedAtIsNull(ObjectIds.parse(messageIdValue))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Message khong ton tai."));
    }

    private MessageDocument requireReactableMessage(ObjectId userId, String messageIdValue, String emoji) {
        if (emoji == null || emoji.isBlank() || emoji.length() > 32) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Emoji khong hop le.");
        }
        MessageDocument message = requireActiveMessage(messageIdValue);
        accessPolicy.requireVisible(userId, message);
        return message;
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
}
