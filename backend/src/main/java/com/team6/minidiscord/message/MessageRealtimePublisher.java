package com.team6.minidiscord.message;

import com.team6.minidiscord.realtime.WebSocketEvent;
import com.team6.minidiscord.realtime.WebSocketEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MessageRealtimePublisher {
    private final WebSocketEventPublisher publisher;
    private final MessageAccessPolicy accessPolicy;

    public MessageRealtimePublisher(WebSocketEventPublisher publisher, MessageAccessPolicy accessPolicy) {
        this.publisher = publisher;
        this.accessPolicy = accessPolicy;
    }

    public void created(MessageDocument message, Object data) {
        publish(message, "MESSAGE_CREATED", "DIRECT_MESSAGE_CREATED", data);
    }

    public void updated(MessageDocument message, Object data) {
        publish(message, "MESSAGE_UPDATED", "DIRECT_MESSAGE_UPDATED", data);
    }

    public void deleted(MessageDocument message, Object data) {
        publish(message, "MESSAGE_DELETED", "DIRECT_MESSAGE_DELETED", data);
    }

    public void reactionUpdated(MessageDocument message, Object data) {
        publish(message, "REACTION_UPDATED", "DIRECT_REACTION_UPDATED", data);
    }

    private void publish(MessageDocument message, String serverEventType, String directEventType, Object data) {
        if (accessPolicy.scope(message) == MessageScope.SERVER) {
            publisher.channelEvent(message.channelId.toHexString(), WebSocketEvent.of(
                    serverEventType,
                    message.serverId.toHexString(),
                    message.channelId.toHexString(),
                    data
            ));
            return;
        }
        publisher.directConversationEvent(message.conversationId.toHexString(), WebSocketEvent.direct(
                directEventType,
                message.conversationId.toHexString(),
                data
        ));
    }
}
