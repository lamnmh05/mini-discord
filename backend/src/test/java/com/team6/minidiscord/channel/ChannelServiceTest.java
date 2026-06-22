package com.team6.minidiscord.channel;

import com.team6.minidiscord.channel.dto.CreateChannelRequest;
import com.team6.minidiscord.channel.dto.DeleteChannelRequest;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.server.ServerRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {
    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private MembershipService membershipService;

    private ChannelService channelService;

    @BeforeEach
    void setUp() {
        channelService = new ChannelService(channelRepository, serverRepository, membershipService);
    }

    @Test
    void createNormalizesNameAndAppendsAfterLastPosition() {
        ObjectId userId = new ObjectId();
        ObjectId serverId = new ObjectId();
        ObjectId defaultChannelId = new ObjectId();
        ServerDocument server = server(serverId, defaultChannelId);
        ChannelDocument latest = channel(serverId, new ObjectId(), "general", 4);

        when(membershipService.requireActiveServer(serverId)).thenReturn(server);
        when(channelRepository.existsByServerIdAndNameKeyAndDeletedAtIsNull(serverId, "qa-room")).thenReturn(false);
        when(channelRepository.findTopByServerIdAndDeletedAtIsNullOrderByPositionDesc(serverId)).thenReturn(Optional.of(latest));
        when(channelRepository.save(any(ChannelDocument.class))).thenAnswer(invocation -> {
            ChannelDocument saved = invocation.getArgument(0);
            saved.id = new ObjectId();
            return saved;
        });

        var response = channelService.create(userId, serverId.toHexString(), new CreateChannelRequest(" QA-Room "));

        verify(membershipService).requireOwner(serverId, userId);
        ArgumentCaptor<ChannelDocument> captor = ArgumentCaptor.forClass(ChannelDocument.class);
        verify(channelRepository).save(captor.capture());
        ChannelDocument saved = captor.getValue();

        assertThat(saved.serverId).isEqualTo(serverId);
        assertThat(saved.createdById).isEqualTo(userId);
        assertThat(saved.name).isEqualTo("QA-Room");
        assertThat(saved.nameKey).isEqualTo("qa-room");
        assertThat(saved.position).isEqualTo(5);
        assertThat(response.name()).isEqualTo("QA-Room");
        assertThat(response.defaultChannel()).isFalse();
    }

    @Test
    void softDeleteDefaultChannelRequiresReplacement() {
        ObjectId userId = new ObjectId();
        ObjectId serverId = new ObjectId();
        ObjectId channelId = new ObjectId();
        ServerDocument server = server(serverId, channelId);
        ChannelDocument channel = channel(serverId, channelId, "general", 0);

        when(channelRepository.findByIdAndDeletedAtIsNull(channelId)).thenReturn(Optional.of(channel));
        when(membershipService.requireActiveServer(serverId)).thenReturn(server);

        assertThatThrownBy(() -> channelService.softDelete(userId, channelId.toHexString(), new DeleteChannelRequest(null)))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ErrorCode.DATA_INTEGRITY_ERROR));

        verify(membershipService).requireOwner(serverId, userId);
        verify(serverRepository, never()).save(any());
        verify(channelRepository, never()).save(any());
    }

    private ServerDocument server(ObjectId id, ObjectId defaultChannelId) {
        ServerDocument server = new ServerDocument();
        server.id = id;
        server.name = "Team 6";
        server.defaultChannelId = defaultChannelId;
        server.createdAt = Instant.now();
        server.updatedAt = server.createdAt;
        return server;
    }

    private ChannelDocument channel(ObjectId serverId, ObjectId channelId, String name, int position) {
        ChannelDocument channel = new ChannelDocument();
        channel.id = channelId;
        channel.serverId = serverId;
        channel.name = name;
        channel.nameKey = name;
        channel.type = ChannelType.TEXT;
        channel.position = position;
        channel.createdAt = Instant.now();
        channel.updatedAt = channel.createdAt;
        return channel;
    }
}
