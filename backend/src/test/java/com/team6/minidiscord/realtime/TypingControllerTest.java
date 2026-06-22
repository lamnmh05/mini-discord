package com.team6.minidiscord.realtime;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelService;
import com.team6.minidiscord.direct.DirectConversationService;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.security.AuthenticatedUser;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypingControllerTest {
    @Mock
    private ChannelService channelService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private DirectConversationService directConversationService;

    @Mock
    private WebSocketEventPublisher publisher;

    private TypingController typingController;

    @BeforeEach
    void setUp() {
        typingController = new TypingController(channelService, membershipService, directConversationService, publisher);
    }

    @Test
    void typingPublishesStartedEventForAuthenticatedChannelMember() {
        ObjectId userId = new ObjectId();
        ObjectId serverId = new ObjectId();
        ObjectId channelId = new ObjectId();
        ChannelDocument channel = new ChannelDocument();
        channel.id = channelId;
        channel.serverId = serverId;

        when(channelService.requireActiveChannel(channelId)).thenReturn(channel);

        typingController.typing(
                channelId.toHexString(),
                Map.of("typing", true),
                headers(userId, "alice")
        );

        verify(membershipService).requireMember(serverId, userId);
        ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
        verify(publisher).typingEvent(org.mockito.ArgumentMatchers.eq(channelId.toHexString()), captor.capture());

        WebSocketEvent<?> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("TYPING_STARTED");
        assertThat(event.serverId()).isEqualTo(serverId.toHexString());
        assertThat(event.channelId()).isEqualTo(channelId.toHexString());
        assertThat(event.data()).isInstanceOfSatisfying(Map.class, data -> {
            assertThat(data).containsEntry("userId", userId.toHexString());
            assertThat(data).containsEntry("username", "alice");
            assertThat(data).containsEntry("typing", true);
        });
    }

    @Test
    void directTypingPublishesStoppedEventForParticipant() {
        ObjectId userId = new ObjectId();
        ObjectId conversationId = new ObjectId();

        typingController.directTyping(
                conversationId.toHexString(),
                Map.of("typing", false),
                headers(userId, "alice")
        );

        verify(directConversationService).requireParticipant(conversationId, userId);
        ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
        verify(publisher).directTypingEvent(org.mockito.ArgumentMatchers.eq(conversationId.toHexString()), captor.capture());

        WebSocketEvent<?> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("DIRECT_TYPING_STOPPED");
        assertThat(event.conversationId()).isEqualTo(conversationId.toHexString());
        assertThat(event.data()).isInstanceOfSatisfying(Map.class, data ->
                assertThat(data).containsEntry("typing", false));
    }

    private SimpMessageHeaderAccessor headers(ObjectId userId, String username) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, username),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        return accessor;
    }
}
