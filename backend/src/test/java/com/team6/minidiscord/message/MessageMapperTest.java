package com.team6.minidiscord.message;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperTest {
    @Test
    void responseMapsServerMessageIds() {
        ObjectId serverId = new ObjectId();
        ObjectId channelId = new ObjectId();
        MessageDocument message = message(MessageScope.SERVER);
        message.serverId = serverId;
        message.channelId = channelId;

        var response = MessageMapper.response(message);

        assertThat(response.scope()).isEqualTo("SERVER");
        assertThat(response.serverId()).isEqualTo(serverId.toHexString());
        assertThat(response.channelId()).isEqualTo(channelId.toHexString());
        assertThat(response.conversationId()).isNull();
    }

    @Test
    void responseAllowsDirectMessageWithoutServerOrChannel() {
        ObjectId conversationId = new ObjectId();
        MessageDocument message = message(MessageScope.DIRECT);
        message.conversationId = conversationId;

        var response = MessageMapper.response(message);

        assertThat(response.scope()).isEqualTo("DIRECT");
        assertThat(response.serverId()).isNull();
        assertThat(response.channelId()).isNull();
        assertThat(response.conversationId()).isEqualTo(conversationId.toHexString());
    }

    private MessageDocument message(MessageScope scope) {
        MessageDocument message = new MessageDocument();
        message.id = new ObjectId();
        message.scope = scope;
        message.senderId = new ObjectId();
        message.content = "hello";
        message.messageType = MessageType.TEXT;
        message.senderSnapshot = new SenderSnapshot();
        message.senderSnapshot.username = "user";
        message.createdAt = Instant.now();
        return message;
    }
}
