package com.team6.minidiscord.membership;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.membership.dto.ChangeRoleRequest;
import com.team6.minidiscord.realtime.PresenceLookupService;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.server.ServerRepository;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {
    @Mock
    private ServerMemberRepository memberRepository;

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PresenceLookupService presenceLookupService;

    private MembershipService membershipService;

    @BeforeEach
    void setUp() {
        membershipService = new MembershipService(memberRepository, serverRepository, userRepository, presenceLookupService);
    }

    @Test
    void leaveRejectsOnlyOwnerLeavingServer() {
        ObjectId serverId = new ObjectId();
        ObjectId ownerId = new ObjectId();
        ServerMemberDocument owner = member(serverId, ownerId, MemberRole.OWNER);

        when(serverRepository.findByIdAndDeletedAtIsNull(serverId)).thenReturn(Optional.of(server(serverId)));
        when(memberRepository.findByServerIdAndUserId(serverId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.countByServerIdAndRole(serverId, MemberRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> membershipService.leave(ownerId, serverId.toHexString()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ErrorCode.OWNER_INVARIANT_VIOLATION));

        verify(memberRepository, never()).deleteByServerIdAndUserId(serverId, ownerId);
    }

    @Test
    void changeRoleRejectsDemotingLastOwner() {
        ObjectId serverId = new ObjectId();
        ObjectId requesterId = new ObjectId();
        ServerMemberDocument requester = member(serverId, requesterId, MemberRole.OWNER);

        when(serverRepository.findByIdAndDeletedAtIsNull(serverId)).thenReturn(Optional.of(server(serverId)));
        when(memberRepository.findByServerIdAndUserId(serverId, requesterId)).thenReturn(Optional.of(requester));
        when(memberRepository.countByServerIdAndRole(serverId, MemberRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> membershipService.changeRole(
                requesterId,
                serverId.toHexString(),
                requesterId.toHexString(),
                new ChangeRoleRequest(MemberRole.MEMBER)
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.code()).isEqualTo(ErrorCode.OWNER_INVARIANT_VIOLATION));

        verify(memberRepository, never()).save(requester);
        verify(userRepository, never()).findById(requesterId);
    }

    @Test
    void changeRoleReturnsUpdatedMemberResponse() {
        ObjectId serverId = new ObjectId();
        ObjectId requesterId = new ObjectId();
        ObjectId targetId = new ObjectId();
        ServerMemberDocument requester = member(serverId, requesterId, MemberRole.OWNER);
        ServerMemberDocument target = member(serverId, targetId, MemberRole.MEMBER);
        UserDocument user = user(targetId, "bob");

        when(serverRepository.findByIdAndDeletedAtIsNull(serverId)).thenReturn(Optional.of(server(serverId)));
        when(memberRepository.findByServerIdAndUserId(serverId, requesterId)).thenReturn(Optional.of(requester));
        when(memberRepository.findByServerIdAndUserId(serverId, targetId)).thenReturn(Optional.of(target));
        when(memberRepository.save(target)).thenReturn(target);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(user));
        when(presenceLookupService.status(targetId)).thenReturn("ONLINE");

        var response = membershipService.changeRole(
                requesterId,
                serverId.toHexString(),
                targetId.toHexString(),
                new ChangeRoleRequest(MemberRole.OWNER)
        );

        assertThat(target.role).isEqualTo(MemberRole.OWNER);
        assertThat(target.updatedAt).isNotNull();
        assertThat(response.userId()).isEqualTo(targetId.toHexString());
        assertThat(response.username()).isEqualTo("bob");
        assertThat(response.role()).isEqualTo(MemberRole.OWNER);
        assertThat(response.presenceStatus()).isEqualTo("ONLINE");
    }

    private ServerDocument server(ObjectId id) {
        ServerDocument server = new ServerDocument();
        server.id = id;
        server.name = "Team";
        return server;
    }

    private ServerMemberDocument member(ObjectId serverId, ObjectId userId, MemberRole role) {
        ServerMemberDocument member = new ServerMemberDocument();
        member.id = new ObjectId();
        member.serverId = serverId;
        member.userId = userId;
        member.role = role;
        member.joinedAt = Instant.now();
        return member;
    }

    private UserDocument user(ObjectId id, String username) {
        UserDocument user = new UserDocument();
        user.id = id;
        user.username = username;
        user.displayName = "Bob";
        return user;
    }
}
