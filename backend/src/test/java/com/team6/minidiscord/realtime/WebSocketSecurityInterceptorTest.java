package com.team6.minidiscord.realtime;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelRepository;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.direct.DirectConversationService;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.security.AuthenticatedUser;
import com.team6.minidiscord.security.JwtService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketSecurityInterceptorTest {
    @Mock
    private JwtService jwtService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private DirectConversationService directConversationService;

    private WebSocketSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketSecurityInterceptor(
                jwtService,
                membershipService,
                channelRepository,
                directConversationService
        );
    }

    @Test
    void connectRequiresBearerToken() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.CONNECT, null), null))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void subscribeToChannelRequiresMembership() {
        ObjectId userId = new ObjectId();
        ObjectId serverId = new ObjectId();
        ObjectId channelId = new ObjectId();
        ChannelDocument channel = new ChannelDocument();
        channel.id = channelId;
        channel.serverId = serverId;

        when(jwtService.verify("access-token")).thenReturn(new AuthenticatedUser(userId, "alice"));
        Message<?> connected = interceptor.preSend(connectMessage("access-token"), null);
        StompHeaderAccessor connectedAccessor = MessageHeaderAccessor.getAccessor(connected, StompHeaderAccessor.class);

        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/channels/" + channelId + "/typing");
        subscribeAccessor.setUser(connectedAccessor.getUser());
        subscribeAccessor.setLeaveMutable(true);
        Message<byte[]> subscribe = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        when(channelRepository.findByIdAndDeletedAtIsNull(channelId)).thenReturn(Optional.of(channel));

        interceptor.preSend(subscribe, null);

        verify(membershipService).requireMember(serverId, userId);
    }

    private Message<byte[]> connectMessage(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> message(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
