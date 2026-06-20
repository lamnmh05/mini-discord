package com.team6.minidiscord.invite;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.invite.dto.DirectInviteRequest;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.notification.NotificationDocument;
import com.team6.minidiscord.notification.NotificationService;
import com.team6.minidiscord.notification.NotificationType;
import com.team6.minidiscord.realtime.WebSocketEvent;
import com.team6.minidiscord.realtime.WebSocketEventPublisher;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.server.ServerRepository;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {
    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private ServerInviteRepository serverInviteRepository;

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WebSocketEventPublisher publisher;

    @Mock
    private MongoTemplate mongoTemplate;

    private InviteService inviteService;

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(
                inviteCodeRepository,
                serverInviteRepository,
                serverRepository,
                userRepository,
                membershipService,
                notificationService,
                publisher,
                mongoTemplate
        );
    }

    @Test
    void sendDirectInviteStoresInviterAndInviteeIds() {
        ObjectId serverId = new ObjectId();
        ObjectId inviterId = new ObjectId();
        ObjectId inviteeId = new ObjectId();

        ServerDocument server = server(serverId, "Team 6");
        UserDocument invitee = user(inviteeId, "bob");
        UserDocument inviter = user(inviterId, "alice");

        when(membershipService.requireActiveServer(serverId)).thenReturn(server);
        when(userRepository.findByUsernameKey("bob")).thenReturn(Optional.of(invitee));
        when(membershipService.isMember(serverId, inviteeId)).thenReturn(false);
        when(serverInviteRepository.findByServerIdAndInviteeIdAndStatus(serverId, inviteeId, InviteStatus.PENDING))
                .thenReturn(Optional.empty());
        when(serverInviteRepository.save(any(ServerInviteDocument.class))).thenAnswer(invocation -> {
            ServerInviteDocument invite = invocation.getArgument(0);
            invite.id = new ObjectId();
            return invite;
        });
        when(notificationService.createServerInvite(eq(inviteeId), eq(inviterId), any(ObjectId.class), eq("Team 6")))
                .thenReturn(notification(inviteeId, inviterId));
        when(userRepository.findById(inviterId)).thenReturn(Optional.of(inviter));

        var response = inviteService.sendDirectInvite(
                inviterId,
                serverId.toHexString(),
                new DirectInviteRequest(" Bob ")
        );

        verify(membershipService).requireOwner(serverId, inviterId);
        verify(userRepository).findByUsernameKey("bob");

        ArgumentCaptor<ServerInviteDocument> captor = ArgumentCaptor.forClass(ServerInviteDocument.class);
        verify(serverInviteRepository).save(captor.capture());
        ServerInviteDocument saved = captor.getValue();

        assertThat(saved.serverId).isEqualTo(serverId);
        assertThat(saved.inviterId).isEqualTo(inviterId);
        assertThat(saved.inviteeId).isEqualTo(inviteeId);
        assertThat(saved.status).isEqualTo(InviteStatus.PENDING);
        assertThat(saved.expiresAt).isAfter(Instant.now());

        verify(publisher).userNotification(eq(inviteeId.toHexString()), any(WebSocketEvent.class));
        assertThat(response.inviterId()).isEqualTo(inviterId.toHexString());
        assertThat(response.inviteeId()).isEqualTo(inviteeId.toHexString());
        assertThat(response.status()).isEqualTo(InviteStatus.PENDING);
    }

    @Test
    void sendDirectInviteRejectsExistingPendingInvite() {
        ObjectId serverId = new ObjectId();
        ObjectId inviterId = new ObjectId();
        ObjectId inviteeId = new ObjectId();

        when(membershipService.requireActiveServer(serverId)).thenReturn(server(serverId, "Team 6"));
        when(userRepository.findByUsernameKey("bob")).thenReturn(Optional.of(user(inviteeId, "bob")));
        when(membershipService.isMember(serverId, inviteeId)).thenReturn(false);
        when(serverInviteRepository.findByServerIdAndInviteeIdAndStatus(serverId, inviteeId, InviteStatus.PENDING))
                .thenReturn(Optional.of(new ServerInviteDocument()));

        assertThatThrownBy(() -> inviteService.sendDirectInvite(
                inviterId,
                serverId.toHexString(),
                new DirectInviteRequest("bob")
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.code()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));

        verify(serverInviteRepository, never()).save(any());
        verify(notificationService, never()).createServerInvite(any(), any(), any(), any());
    }

    private ServerDocument server(ObjectId id, String name) {
        ServerDocument server = new ServerDocument();
        server.id = id;
        server.name = name;
        return server;
    }

    private UserDocument user(ObjectId id, String username) {
        UserDocument user = new UserDocument();
        user.id = id;
        user.username = username;
        user.usernameKey = username;
        return user;
    }

    private NotificationDocument notification(ObjectId userId, ObjectId actorId) {
        NotificationDocument notification = new NotificationDocument();
        notification.id = new ObjectId();
        notification.userId = userId;
        notification.actorId = actorId;
        notification.serverInviteId = new ObjectId();
        notification.type = NotificationType.SERVER_INVITE;
        notification.title = "Server invite";
        notification.body = "Invite";
        notification.createdAt = Instant.now();
        return notification;
    }
}
