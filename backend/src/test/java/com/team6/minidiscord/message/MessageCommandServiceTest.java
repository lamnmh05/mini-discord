package com.team6.minidiscord.message;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.direct.DirectConversationService;
import com.team6.minidiscord.file.FileStorageService;
import com.team6.minidiscord.message.dto.CreateMessageRequest;
import com.team6.minidiscord.user.UserService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageCommandServiceTest {
    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageAccessPolicy accessPolicy;

    @Mock
    private DirectConversationService directConversationService;

    @Mock
    private UserService userService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MessageRealtimePublisher realtimePublisher;

    private MessageCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new MessageCommandService(
                messageRepository,
                accessPolicy,
                directConversationService,
                userService,
                fileStorageService,
                realtimePublisher
        );
    }

    @Test
    void sendToChannelReturnsExistingIdempotentMessageWithoutSavingAgain() {
        ObjectId userId = new ObjectId();
        ObjectId serverId = new ObjectId();
        ObjectId channelId = new ObjectId();
        String clientRequestId = "client-1";
        MessageDocument existing = message(userId, serverId, channelId, "hello");
        existing.clientRequestId = clientRequestId;

        when(accessPolicy.requireServerChannel(userId, channelId)).thenReturn(channel(serverId, channelId));
        when(messageRepository.findBySenderIdAndClientRequestId(userId, clientRequestId)).thenReturn(Optional.of(existing));

        var response = commandService.sendToChannel(
                userId,
                channelId.toHexString(),
                new CreateMessageRequest("ignored retry body", List.of(), clientRequestId)
        );

        assertThat(response.id()).isEqualTo(existing.id.toHexString());
        assertThat(response.content()).isEqualTo("hello");
        verify(fileStorageService, never()).verifyOwnership(any(), any());
        verify(messageRepository, never()).save(any());
        verify(realtimePublisher, never()).created(any(), any());
    }

    @Test
    void sendToChannelRejectsEmptyMessageWithoutPersisting() {
        ObjectId userId = new ObjectId();
        ObjectId serverId = new ObjectId();
        ObjectId channelId = new ObjectId();

        when(accessPolicy.requireServerChannel(userId, channelId)).thenReturn(channel(serverId, channelId));

        assertThatThrownBy(() -> commandService.sendToChannel(
                userId,
                channelId.toHexString(),
                new CreateMessageRequest("   ", List.of(), null)
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(userService, never()).getActiveUser(any());
        verify(fileStorageService, never()).verifyOwnership(any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void addReactionDoesNotDuplicateSameUser() {
        ObjectId userId = new ObjectId();
        ObjectId serverId = new ObjectId();
        ObjectId channelId = new ObjectId();
        MessageDocument message = message(userId, serverId, channelId, "hello");
        Reaction reaction = new Reaction();
        reaction.emoji = "ok";
        reaction.userIds.add(userId);
        message.reactions.add(reaction);

        when(messageRepository.findByIdAndDeletedAtIsNull(message.id)).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);

        var response = commandService.addReaction(userId, message.id.toHexString(), "ok");

        assertThat(message.reactions).hasSize(1);
        assertThat(message.reactions.get(0).userIds).containsExactly(userId);
        assertThat(response.reactions()).hasSize(1);
        assertThat(response.reactions().get(0).userIds()).containsExactly(userId.toHexString());
        verify(accessPolicy).requireVisible(userId, message);
        verify(realtimePublisher).reactionUpdated(message, response);
    }

    private ChannelDocument channel(ObjectId serverId, ObjectId channelId) {
        ChannelDocument channel = new ChannelDocument();
        channel.id = channelId;
        channel.serverId = serverId;
        return channel;
    }

    private MessageDocument message(ObjectId userId, ObjectId serverId, ObjectId channelId, String content) {
        MessageDocument message = new MessageDocument();
        message.id = new ObjectId();
        message.scope = MessageScope.SERVER;
        message.serverId = serverId;
        message.channelId = channelId;
        message.senderId = userId;
        message.content = content;
        message.messageType = MessageType.TEXT;
        message.senderSnapshot = new SenderSnapshot();
        message.senderSnapshot.username = "alice";
        message.attachments = List.of();
        message.createdAt = Instant.now();
        message.updatedAt = message.createdAt;
        return message;
    }

}
