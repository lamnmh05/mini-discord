package com.team6.minidiscord.realtime;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelService;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.direct.DirectConversationService;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.security.AuthenticatedUser;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class TypingController {
    private final ChannelService channelService;
    private final MembershipService membershipService;
    private final DirectConversationService directConversationService;
    private final WebSocketEventPublisher publisher;

    public TypingController(
            ChannelService channelService,
            MembershipService membershipService,
            DirectConversationService directConversationService,
            WebSocketEventPublisher publisher
    ) {
        this.channelService = channelService;
        this.membershipService = membershipService;
        this.directConversationService = directConversationService;
        this.publisher = publisher;
    }

    @MessageMapping("/channels/{channelId}/typing")
    public void typing(@DestinationVariable String channelId, Map<String, Boolean> payload, SimpMessageHeaderAccessor headers) {
        AuthenticatedUser user = currentUser(headers);
        ChannelDocument channel = channelService.requireActiveChannel(ObjectIds.parse(channelId));
        membershipService.requireMember(channel.serverId, user.id());
        boolean typing = payload != null && Boolean.TRUE.equals(payload.get("typing"));
        publisher.typingEvent(channelId, WebSocketEvent.of(
                typing ? "TYPING_STARTED" : "TYPING_STOPPED",
                channel.serverId.toHexString(),
                channelId,
                Map.of("userId", user.id().toHexString(), "username", user.username(), "typing", typing)
        ));
    }

    @MessageMapping("/direct-conversations/{conversationId}/typing")
    public void directTyping(@DestinationVariable String conversationId, Map<String, Boolean> payload, SimpMessageHeaderAccessor headers) {
        AuthenticatedUser user = currentUser(headers);
        directConversationService.requireParticipant(ObjectIds.parse(conversationId), user.id());
        boolean typing = payload != null && Boolean.TRUE.equals(payload.get("typing"));
        publisher.directTypingEvent(conversationId, WebSocketEvent.direct(
                typing ? "DIRECT_TYPING_STARTED" : "DIRECT_TYPING_STOPPED",
                conversationId,
                Map.of("userId", user.id().toHexString(), "username", user.username(), "typing", typing)
        ));
    }

    private AuthenticatedUser currentUser(SimpMessageHeaderAccessor headers) {
        if (headers.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED, "WebSocket is not authenticated.");
    }
}
