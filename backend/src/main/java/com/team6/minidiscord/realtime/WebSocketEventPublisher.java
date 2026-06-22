package com.team6.minidiscord.realtime;

import com.team6.minidiscord.common.util.TransactionEvents;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void channelEvent(String channelId, WebSocketEvent<?> event) {
        TransactionEvents.afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/messages",
                event
        ));
    }

    public void directConversationEvent(String conversationId, WebSocketEvent<?> event) {
        TransactionEvents.afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/direct-conversations/" + conversationId + "/messages",
                event
        ));
    }

    public void serverChannelsEvent(String serverId, WebSocketEvent<?> event) {
        TransactionEvents.afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/servers/" + serverId + "/channels",
                event
        ));
    }

    public void serverMembersEvent(String serverId, WebSocketEvent<?> event) {
        TransactionEvents.afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/servers/" + serverId + "/members",
                event
        ));
    }

    public void serverPresenceEvent(String serverId, WebSocketEvent<?> event) {
        messagingTemplate.convertAndSend("/topic/servers/" + serverId + "/presence", event);
    }

    public void typingEvent(String channelId, WebSocketEvent<?> event) {
        messagingTemplate.convertAndSend("/topic/channels/" + channelId + "/typing", event);
    }

    public void directTypingEvent(String conversationId, WebSocketEvent<?> event) {
        messagingTemplate.convertAndSend("/topic/direct-conversations/" + conversationId + "/typing", event);
    }

    public void userNotification(String userId, WebSocketEvent<?> event) {
        TransactionEvents.afterCommit(() -> messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                event
        ));
    }
}
