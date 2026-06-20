package com.team6.minidiscord.channel;

import com.team6.minidiscord.channel.dto.ChannelResponse;
import com.team6.minidiscord.channel.dto.CreateChannelRequest;
import com.team6.minidiscord.channel.dto.DeleteChannelRequest;
import com.team6.minidiscord.channel.dto.UpdateChannelRequest;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.Keys;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.server.ServerRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ChannelService {
    private final ChannelRepository channelRepository;
    private final ServerRepository serverRepository;
    private final MembershipService membershipService;

    public ChannelService(ChannelRepository channelRepository, ServerRepository serverRepository, MembershipService membershipService) {
        this.channelRepository = channelRepository;
        this.serverRepository = serverRepository;
        this.membershipService = membershipService;
    }

    public ChannelDocument requireActiveChannel(ObjectId channelId) {
        ChannelDocument channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Channel không tồn tại."));
        membershipService.requireActiveServer(channel.serverId);
        return channel;
    }

    public List<ChannelResponse> list(ObjectId userId, String serverIdValue) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireMember(serverId, userId);
        ServerDocument server = membershipService.requireActiveServer(serverId);
        return channelRepository.findByServerIdAndDeletedAtIsNullOrderByPositionAsc(serverId).stream()
                .map(channel -> ChannelMapper.response(channel, server.defaultChannelId))
                .toList();
    }

    public ChannelResponse create(ObjectId userId, String serverIdValue, CreateChannelRequest request) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireOwner(serverId, userId);
        ServerDocument server = membershipService.requireActiveServer(serverId);
        String nameKey = Keys.normalize(request.name());
        if (channelRepository.existsByServerIdAndNameKeyAndDeletedAtIsNull(serverId, nameKey)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Channel đã tồn tại.");
        }
        int nextPosition = channelRepository.findTopByServerIdAndDeletedAtIsNullOrderByPositionDesc(serverId)
                .map(channel -> channel.position + 1)
                .orElse(0);
        Instant now = Instant.now();
        ChannelDocument channel = new ChannelDocument();
        channel.serverId = serverId;
        channel.createdById = userId;
        channel.name = request.name().trim();
        channel.nameKey = nameKey;
        channel.type = ChannelType.TEXT;
        channel.position = nextPosition;
        channel.createdAt = now;
        channel.updatedAt = now;
        return ChannelMapper.response(channelRepository.save(channel), server.defaultChannelId);
    }

    public ChannelResponse update(ObjectId userId, String channelIdValue, UpdateChannelRequest request) {
        ObjectId channelId = ObjectIds.parse(channelIdValue);
        ChannelDocument channel = requireActiveChannel(channelId);
        membershipService.requireOwner(channel.serverId, userId);
        if (request.name() != null) {
            String nameKey = Keys.normalize(request.name());
            if (!nameKey.equals(channel.nameKey) && channelRepository.existsByServerIdAndNameKeyAndDeletedAtIsNull(channel.serverId, nameKey)) {
                throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Channel đã tồn tại.");
            }
            channel.name = request.name().trim();
            channel.nameKey = nameKey;
        }
        if (request.position() != null) {
            channel.position = request.position();
        }
        channel.updatedAt = Instant.now();
        ServerDocument server = membershipService.requireActiveServer(channel.serverId);
        return ChannelMapper.response(channelRepository.save(channel), server.defaultChannelId);
    }

    @Transactional
    public void softDelete(ObjectId userId, String channelIdValue, DeleteChannelRequest request) {
        ObjectId channelId = ObjectIds.parse(channelIdValue);
        ChannelDocument channel = requireActiveChannel(channelId);
        membershipService.requireOwner(channel.serverId, userId);
        ServerDocument server = membershipService.requireActiveServer(channel.serverId);
        Instant now = Instant.now();
        if (channel.id.equals(server.defaultChannelId)) {
            if (request == null || request.replacementChannelId() == null) {
                throw new ApiException(ErrorCode.DATA_INTEGRITY_ERROR, "Cần replacementChannelId khi xóa default channel.");
            }
            ObjectId replacementId = ObjectIds.parse(request.replacementChannelId());
            if (replacementId.equals(channelId)) {
                throw new ApiException(ErrorCode.DATA_INTEGRITY_ERROR, "replacementChannelId không hợp lệ.");
            }
            ChannelDocument replacement = channelRepository.findByIdAndDeletedAtIsNull(replacementId)
                    .filter(candidate -> candidate.serverId.equals(channel.serverId))
                    .orElseThrow(() -> new ApiException(ErrorCode.DATA_INTEGRITY_ERROR, "replacementChannelId không hợp lệ."));
            server.defaultChannelId = replacement.id;
            server.updatedAt = now;
            serverRepository.save(server);
        }
        channel.deletedAt = now;
        channel.updatedAt = now;
        channelRepository.save(channel);
    }
}
