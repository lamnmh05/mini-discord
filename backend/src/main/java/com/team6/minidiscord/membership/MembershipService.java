package com.team6.minidiscord.membership;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.membership.dto.ChangeRoleRequest;
import com.team6.minidiscord.membership.dto.MemberResponse;
import com.team6.minidiscord.realtime.PresenceLookupService;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.server.ServerRepository;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MembershipService {
    private final ServerMemberRepository memberRepository;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final PresenceLookupService presenceLookupService;

    public MembershipService(
            ServerMemberRepository memberRepository,
            ServerRepository serverRepository,
            UserRepository userRepository,
            PresenceLookupService presenceLookupService
    ) {
        this.memberRepository = memberRepository;
        this.serverRepository = serverRepository;
        this.userRepository = userRepository;
        this.presenceLookupService = presenceLookupService;
    }

    public ServerMemberDocument requireMember(ObjectId serverId, ObjectId userId) {
        requireActiveServer(serverId);
        return memberRepository.findByServerIdAndUserId(serverId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Bạn không thuộc server này."));
    }

    public ServerMemberDocument requireOwner(ObjectId serverId, ObjectId userId) {
        ServerMemberDocument member = requireMember(serverId, userId);
        if (member.role != MemberRole.OWNER) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Bạn không có quyền OWNER.");
        }
        return member;
    }

    public ServerDocument requireActiveServer(ObjectId serverId) {
        return serverRepository.findByIdAndDeletedAtIsNull(serverId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Server không tồn tại."));
    }

    public boolean isMember(ObjectId serverId, ObjectId userId) {
        return serverRepository.findByIdAndDeletedAtIsNull(serverId).isPresent()
                && memberRepository.existsByServerIdAndUserId(serverId, userId);
    }

    public List<MemberResponse> listMembers(ObjectId requesterId, String serverIdValue) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        requireMember(serverId, requesterId);
        List<ServerMemberDocument> members = memberRepository.findByServerId(serverId);
        Map<ObjectId, ServerMemberDocument> byUser = members.stream()
                .collect(Collectors.toMap(m -> m.userId, Function.identity()));
        List<UserDocument> users = userRepository.findByIdIn(byUser.keySet());
        return users.stream()
                .map(user -> {
                    ServerMemberDocument member = byUser.get(user.id);
                    return new MemberResponse(
                            user.id.toHexString(),
                            user.username,
                            user.displayName,
                            user.avatarUrl,
                            user.customStatus,
                            user.lastSeenAt,
                            presenceLookupService.status(user.id),
                            member.role,
                            member.joinedAt
                    );
                })
                .sorted(Comparator.comparing((MemberResponse m) -> m.role() == MemberRole.OWNER ? 0 : 1)
                        .thenComparing(MemberResponse::username))
                .toList();
    }

    @Transactional
    public void leave(ObjectId userId, String serverIdValue) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        ServerMemberDocument member = requireMember(serverId, userId);
        if (member.role == MemberRole.OWNER && memberRepository.countByServerIdAndRole(serverId, MemberRole.OWNER) <= 1) {
            throw new ApiException(ErrorCode.OWNER_INVARIANT_VIOLATION, "OWNER duy nhất không thể rời server.");
        }
        memberRepository.deleteByServerIdAndUserId(serverId, userId);
    }

    @Transactional
    public void kick(ObjectId requesterId, String serverIdValue, String targetUserIdValue) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        ObjectId targetUserId = ObjectIds.parse(targetUserIdValue);
        requireOwner(serverId, requesterId);
        if (requesterId.equals(targetUserId)) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "OWNER không thể kick chính mình.");
        }
        ServerMemberDocument target = memberRepository.findByServerIdAndUserId(serverId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Member không tồn tại."));
        if (target.role == MemberRole.OWNER) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Không thể kick OWNER khác.");
        }
        memberRepository.delete(target);
    }

    @Transactional
    public MemberResponse changeRole(ObjectId requesterId, String serverIdValue, String targetUserIdValue, ChangeRoleRequest request) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        ObjectId targetUserId = ObjectIds.parse(targetUserIdValue);
        requireOwner(serverId, requesterId);
        ServerMemberDocument target = memberRepository.findByServerIdAndUserId(serverId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Member không tồn tại."));
        if (target.role == MemberRole.OWNER && request.role() == MemberRole.MEMBER
                && memberRepository.countByServerIdAndRole(serverId, MemberRole.OWNER) <= 1) {
            throw new ApiException(ErrorCode.OWNER_INVARIANT_VIOLATION, "Server phải luôn có ít nhất một OWNER.");
        }
        target.role = request.role();
        target.updatedAt = Instant.now();
        memberRepository.save(target);
        UserDocument user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User không tồn tại."));
        return new MemberResponse(
                user.id.toHexString(),
                user.username,
                user.displayName,
                user.avatarUrl,
                user.customStatus,
                user.lastSeenAt,
                presenceLookupService.status(user.id),
                target.role,
                target.joinedAt
        );
    }

    public ServerMemberDocument createMember(ObjectId serverId, ObjectId userId, MemberRole role) {
        Instant now = Instant.now();
        ServerMemberDocument member = new ServerMemberDocument();
        member.serverId = serverId;
        member.userId = userId;
        member.role = role;
        member.joinedAt = now;
        member.updatedAt = now;
        return memberRepository.save(member);
    }
}
