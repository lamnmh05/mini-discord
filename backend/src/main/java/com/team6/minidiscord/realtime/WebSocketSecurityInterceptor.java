package com.team6.minidiscord.realtime;

import com.team6.minidiscord.channel.ChannelRepository;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.security.AuthenticatedUser;
import com.team6.minidiscord.security.JwtService;
import org.bson.types.ObjectId;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
public class WebSocketSecurityInterceptor implements ChannelInterceptor {
    private final JwtService jwtService;
    private final MembershipService membershipService;
    private final ChannelRepository channelRepository;

    public WebSocketSecurityInterceptor(JwtService jwtService, MembershipService membershipService, ChannelRepository channelRepository) {
        this.jwtService = jwtService;
        this.membershipService = membershipService;
        this.channelRepository = channelRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "Thiếu access token WebSocket.");
            }
            AuthenticatedUser user = jwtService.verify(authorization.substring("Bearer ".length()));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            accessor.setUser(authentication);
            return message;
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE || accessor.getCommand() == StompCommand.SEND) {
            AuthenticatedUser user = currentUser(accessor.getUser());
            authorizeDestination(user.id(), accessor.getDestination());
        }
        return message;
    }

    private AuthenticatedUser currentUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED, "WebSocket chưa xác thực.");
    }

    private void authorizeDestination(ObjectId userId, String destination) {
        if (destination == null || destination.startsWith("/user/queue/notifications")) {
            return;
        }
        if (destination.startsWith("/topic/channels/") || destination.startsWith("/app/channels/")) {
            String channelId = destination.split("/")[3];
            ObjectId id = ObjectIds.parse(channelId);
            channelRepository.findByIdAndDeletedAtIsNull(id)
                    .ifPresentOrElse(
                            ch -> membershipService.requireMember(ch.serverId, userId),
                            () -> {
                                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Channel không tồn tại.");
                            }
                    );
            return;
        }
        if (destination.startsWith("/topic/servers/")) {
            String serverId = destination.split("/")[3];
            membershipService.requireMember(ObjectIds.parse(serverId), userId);
        }
    }
}
