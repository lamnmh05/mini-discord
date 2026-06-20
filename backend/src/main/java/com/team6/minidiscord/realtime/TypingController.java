package com.team6.minidiscord.realtime;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelService;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.security.AuthenticatedUser;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class TypingController {
    private final ChannelService channelService;
    private final MembershipService membershipService;
    private final WebSocketEventPublisher publisher;

    public TypingController(ChannelService channelService, MembershipService membershipService, WebSocketEventPublisher publisher) {
        this.channelService = channelService;
        this.membershipService = membershipService;
        this.publisher = publisher;
    }

    @MessageMapping("/channels/{channelId}/typing")
    public void typing(@DestinationVariable String channelId, Map<String, Boolean> payload, SimpMessageHeaderAccessor headers) {
        AuthenticatedUser user = (AuthenticatedUser) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) headers.getUser()).getPrincipal();
        ChannelDocument channel = channelService.requireActiveChannel(com.team6.minidiscord.common.util.ObjectIds.parse(channelId));
        membershipService.requireMember(channel.serverId, user.id());
        boolean typing = Boolean.TRUE.equals(payload.get("typing"));
        publisher.typingEvent(channelId, WebSocketEvent.of(
                typing ? "TYPING_STARTED" : "TYPING_STOPPED",
                channel.serverId.toHexString(),
                channelId,
                Map.of("userId", user.id().toHexString(), "username", user.username(), "typing", typing)
        ));
    }
}
