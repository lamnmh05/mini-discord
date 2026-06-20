package com.team6.minidiscord.server;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelRepository;
import com.team6.minidiscord.channel.ChannelType;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.invite.InviteCodeRepository;
import com.team6.minidiscord.invite.InviteStatus;
import com.team6.minidiscord.invite.ServerInviteRepository;
import com.team6.minidiscord.membership.MemberRole;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.membership.ServerMemberDocument;
import com.team6.minidiscord.membership.ServerMemberRepository;
import com.team6.minidiscord.server.dto.CreateServerRequest;
import com.team6.minidiscord.server.dto.ServerResponse;
import com.team6.minidiscord.server.dto.UpdateServerRequest;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ServerService {
    private final ServerRepository serverRepository;
    private final ServerMemberRepository memberRepository;
    private final ChannelRepository channelRepository;
    private final MembershipService membershipService;
    private final InviteCodeRepository inviteCodeRepository;
    private final ServerInviteRepository serverInviteRepository;

    public ServerService(
            ServerRepository serverRepository,
            ServerMemberRepository memberRepository,
            ChannelRepository channelRepository,
            MembershipService membershipService,
            InviteCodeRepository inviteCodeRepository,
            ServerInviteRepository serverInviteRepository
    ) {
        this.serverRepository = serverRepository;
        this.memberRepository = memberRepository;
        this.channelRepository = channelRepository;
        this.membershipService = membershipService;
        this.inviteCodeRepository = inviteCodeRepository;
        this.serverInviteRepository = serverInviteRepository;
    }

    @Transactional
    public ServerResponse create(ObjectId userId, CreateServerRequest request) {
        Instant now = Instant.now();
        ServerDocument server = new ServerDocument();
        server.name = request.name().trim();
        server.iconUrl = request.iconUrl();
        server.createdById = userId;
        server.createdAt = now;
        server.updatedAt = now;
        server = serverRepository.save(server);

        membershipService.createMember(server.id, userId, MemberRole.OWNER);

        ChannelDocument general = new ChannelDocument();
        general.serverId = server.id;
        general.createdById = userId;
        general.name = "general";
        general.nameKey = "general";
        general.type = ChannelType.TEXT;
        general.position = 0;
        general.createdAt = now;
        general.updatedAt = now;
        general = channelRepository.save(general);

        server.defaultChannelId = general.id;
        server.updatedAt = now;
        server = serverRepository.save(server);
        return ServerMapper.response(server, MemberRole.OWNER);
    }

    public List<ServerResponse> listJoined(ObjectId userId) {
        List<ServerMemberDocument> memberships = memberRepository.findByUserIdOrderByJoinedAtDesc(userId);
        Map<ObjectId, ServerMemberDocument> membershipByServer = memberships.stream()
                .collect(Collectors.toMap(m -> m.serverId, Function.identity(), (a, b) -> a));
        return serverRepository.findByIdInAndDeletedAtIsNull(membershipByServer.keySet()).stream()
                .map(server -> ServerMapper.response(server, membershipByServer.get(server.id).role))
                .toList();
    }

    public ServerResponse detail(ObjectId userId, String serverIdValue) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        ServerMemberDocument membership = membershipService.requireMember(serverId, userId);
        ServerDocument server = membershipService.requireActiveServer(serverId);
        if (server.defaultChannelId == null || channelRepository.findByIdAndDeletedAtIsNull(server.defaultChannelId).isEmpty()) {
            throw new ApiException(ErrorCode.DATA_INTEGRITY_ERROR, "defaultChannelId không hợp lệ.");
        }
        return ServerMapper.response(server, membership.role);
    }

    public ServerResponse update(ObjectId userId, String serverIdValue, UpdateServerRequest request) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireOwner(serverId, userId);
        ServerDocument server = membershipService.requireActiveServer(serverId);
        if (request.name() != null) {
            server.name = request.name().trim();
        }
        if (request.iconUrl() != null) {
            server.iconUrl = request.iconUrl();
        }
        server.updatedAt = Instant.now();
        return ServerMapper.response(serverRepository.save(server), MemberRole.OWNER);
    }

    @Transactional
    public void softDelete(ObjectId userId, String serverIdValue) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireOwner(serverId, userId);
        ServerDocument server = membershipService.requireActiveServer(serverId);
        Instant now = Instant.now();
        server.deletedAt = now;
        server.updatedAt = now;
        serverRepository.save(server);

        channelRepository.findByServerIdAndDeletedAtIsNullOrderByPositionAsc(serverId).forEach(channel -> {
            channel.deletedAt = now;
            channel.updatedAt = now;
            channelRepository.save(channel);
        });
        inviteCodeRepository.findByServerIdOrderByCreatedAtDesc(serverId).forEach(invite -> {
            if (invite.revokedAt == null) {
                invite.revokedAt = now;
                inviteCodeRepository.save(invite);
            }
        });
        serverInviteRepository.findAll().stream()
                .filter(invite -> serverId.equals(invite.serverId) && invite.status == InviteStatus.PENDING)
                .forEach(invite -> {
                    invite.status = InviteStatus.CANCELLED;
                    invite.updatedAt = now;
                    serverInviteRepository.save(invite);
                });
    }
}
