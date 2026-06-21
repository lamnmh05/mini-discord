package com.team6.minidiscord.invite;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.Keys;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.invite.dto.CreateInviteCodeRequest;
import com.team6.minidiscord.invite.dto.DirectInviteRequest;
import com.team6.minidiscord.invite.dto.InviteCodeResponse;
import com.team6.minidiscord.invite.dto.ServerInviteResponse;
import com.team6.minidiscord.membership.MemberRole;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.notification.NotificationDocument;
import com.team6.minidiscord.notification.NotificationMapper;
import com.team6.minidiscord.notification.NotificationService;
import com.team6.minidiscord.realtime.WebSocketEvent;
import com.team6.minidiscord.realtime.WebSocketEventPublisher;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.server.ServerMapper;
import com.team6.minidiscord.server.ServerRepository;
import com.team6.minidiscord.server.dto.ServerResponse;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class InviteService {
    private final InviteCodeRepository inviteCodeRepository;
    private final ServerInviteRepository serverInviteRepository;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final NotificationService notificationService;
    private final WebSocketEventPublisher publisher;
    private final MongoTemplate mongoTemplate;
    private final SecureRandom random = new SecureRandom();

    public InviteService(
            InviteCodeRepository inviteCodeRepository,
            ServerInviteRepository serverInviteRepository,
            ServerRepository serverRepository,
            UserRepository userRepository,
            MembershipService membershipService,
            NotificationService notificationService,
            WebSocketEventPublisher publisher,
            MongoTemplate mongoTemplate
    ) {
        this.inviteCodeRepository = inviteCodeRepository;
        this.serverInviteRepository = serverInviteRepository;
        this.serverRepository = serverRepository;
        this.userRepository = userRepository;
        this.membershipService = membershipService;
        this.notificationService = notificationService;
        this.publisher = publisher;
        this.mongoTemplate = mongoTemplate;
    }

    public InviteCodeResponse createCode(ObjectId userId, String serverIdValue, CreateInviteCodeRequest request) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireOwner(serverId, userId);
        membershipService.requireActiveServer(serverId);
        InviteCodeDocument invite = new InviteCodeDocument();
        invite.serverId = serverId;
        invite.createdById = userId;
        invite.code = uniqueCode();
        invite.maxUses = request.maxUses();
        invite.useCount = 0;
        if (request.expiresAt() != null) {
            invite.expiresAt = request.expiresAt();
        } else {
            invite.expiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60));
        }
        invite.createdAt = Instant.now();
        return InviteMapper.code(inviteCodeRepository.save(invite));
    }

    public List<InviteCodeResponse> listCodes(ObjectId userId, String serverIdValue) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireOwner(serverId, userId);
        return inviteCodeRepository.findByServerIdOrderByCreatedAtDesc(serverId).stream()
                .map(InviteMapper::code)
                .toList();
    }

    public void revokeCode(ObjectId userId, String inviteCodeIdValue) {
        InviteCodeDocument invite = inviteCodeRepository.findById(ObjectIds.parse(inviteCodeIdValue))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Invite code không tồn tại."));
        membershipService.requireOwner(invite.serverId, userId);
        if (invite.revokedAt == null) {
            invite.revokedAt = Instant.now();
            inviteCodeRepository.save(invite);
        }
    }

    @Transactional
    public ServerResponse joinByCode(ObjectId userId, String code) {
        InviteCodeDocument invite = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_NOT_USABLE, "Invite code không hợp lệ."));
        ServerDocument server = serverRepository.findByIdAndDeletedAtIsNull(invite.serverId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Server không tồn tại."));
        if (!usable(invite)) {
            throw new ApiException(ErrorCode.INVITE_NOT_USABLE, "Invite code không còn hiệu lực.");
        }
        if (membershipService.isMember(invite.serverId, userId)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "User đã là member của server.");
        }
        Query query = new Query(Criteria.where("_id").is(invite.id).and("revokedAt").is(null));
        if (invite.expiresAt != null) {
            query.addCriteria(Criteria.where("expiresAt").gt(Instant.now()));
        }
        // FIX 1: Bỏ qua query điều kiện maxUses nếu người tạo set = 0 (Không giới hạn)
        if (invite.maxUses != null && invite.maxUses > 0) {
            query.addCriteria(Criteria.where("useCount").lt(invite.maxUses));
        }
        var result = mongoTemplate.updateFirst(query, new Update().inc("useCount", 1), InviteCodeDocument.class);
        if (result.getModifiedCount() != 1) {
            throw new ApiException(ErrorCode.INVITE_NOT_USABLE, "Invite code không còn hiệu lực.");
        }
        membershipService.createMember(invite.serverId, userId, MemberRole.MEMBER);
        return ServerMapper.response(server, MemberRole.MEMBER);
    }

    @Transactional
    public ServerInviteResponse sendDirectInvite(ObjectId inviterId, String serverIdValue, DirectInviteRequest request) {
        ObjectId serverId = ObjectIds.parse(serverIdValue);
        membershipService.requireOwner(serverId, inviterId);
        ServerDocument server = membershipService.requireActiveServer(serverId);
        UserDocument invitee = userRepository.findByUsernameKey(Keys.normalize(request.inviteeUsername()))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User nhận invite không tồn tại."));
        if (invitee.id.equals(inviterId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Không thể tự invite chính mình.");
        }
        if (membershipService.isMember(serverId, invitee.id)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "User đã là member của server.");
        }
        serverInviteRepository.findByServerIdAndInviteeIdAndStatus(serverId, invitee.id, InviteStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Đã có direct invite PENDING.");
                });
        Instant now = Instant.now();
        ServerInviteDocument invite = new ServerInviteDocument();
        invite.serverId = serverId;
        invite.inviterId = inviterId;
        invite.inviteeId = invitee.id;
        invite.status = InviteStatus.PENDING;
        invite.expiresAt = now.plusSeconds(7 * 24 * 60 * 60);
        invite.createdAt = now;
        invite.updatedAt = now;
        invite = serverInviteRepository.save(invite);
        NotificationDocument notification = notificationService.createServerInvite(invitee.id, inviterId, invite.id, server.name);
        publisher.userNotification(invitee.id.toHexString(), WebSocketEvent.of(
                "NOTIFICATION_CREATED",
                serverId.toHexString(),
                null,
                NotificationMapper.response(notification)
        ));
        UserDocument inviter = userRepository.findById(inviterId).orElse(null);
        return InviteMapper.direct(invite, server, inviter);
    }

    public List<ServerInviteResponse> received(ObjectId inviteeId) {
        expirePending(inviteeId);
        return serverInviteRepository.findByInviteeIdAndStatusOrderByCreatedAtDesc(inviteeId, InviteStatus.PENDING).stream()
                .map(invite -> InviteMapper.direct(
                        invite,
                        serverRepository.findByIdAndDeletedAtIsNull(invite.serverId).orElse(null),
                        userRepository.findById(invite.inviterId).orElse(null)
                ))
                .toList();
    }

    @Transactional
    public ServerResponse accept(ObjectId inviteeId, String inviteIdValue) {
        ServerInviteDocument invite = requireInviteePending(inviteeId, inviteIdValue);
        ServerDocument server = serverRepository.findByIdAndDeletedAtIsNull(invite.serverId)
                .orElseThrow(() -> {
                    invite.status = InviteStatus.CANCELLED;
                    invite.updatedAt = Instant.now();
                    serverInviteRepository.save(invite);
                    return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Server không tồn tại.");
                });

        // FIX 2: Bỏ hàm save bị thừa và gây lỗi rollback ở đây. Chặn chuẩn xác bằng !isAfter()
        if (!invite.expiresAt.isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.INVITE_NOT_USABLE, "Direct invite đã hết hạn.");
        }

        if (membershipService.isMember(invite.serverId, inviteeId)) {
            invite.status = InviteStatus.CANCELLED;
            invite.updatedAt = Instant.now();
            serverInviteRepository.save(invite);
            notificationService.markServerInviteRead(inviteeId, invite.id);
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "User đã là member của server.");
        }
        membershipService.createMember(invite.serverId, inviteeId, MemberRole.MEMBER);
        invite.status = InviteStatus.ACCEPTED;
        invite.respondedAt = Instant.now();
        invite.updatedAt = invite.respondedAt;
        serverInviteRepository.save(invite);
        notificationService.markServerInviteRead(inviteeId, invite.id);
        return ServerMapper.response(server, MemberRole.MEMBER);
    }

    @Transactional
    public void reject(ObjectId inviteeId, String inviteIdValue) {
        ServerInviteDocument invite = requireInviteePending(inviteeId, inviteIdValue);
        if (invite.expiresAt.isBefore(Instant.now())) {
            invite.status = InviteStatus.EXPIRED;
        } else {
            invite.status = InviteStatus.REJECTED;
            invite.respondedAt = Instant.now();
        }
        invite.updatedAt = Instant.now();
        serverInviteRepository.save(invite);
        notificationService.markServerInviteRead(inviteeId, invite.id);
    }

    private ServerInviteDocument requireInviteePending(ObjectId inviteeId, String inviteIdValue) {
        ServerInviteDocument invite = serverInviteRepository.findByIdAndInviteeId(ObjectIds.parse(inviteIdValue), inviteeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Direct invite không tồn tại."));
        if (invite.status != InviteStatus.PENDING) {
            throw new ApiException(ErrorCode.INVITE_NOT_USABLE, "Direct invite không còn hiệu lực.");
        }
        return invite;
    }

    private boolean usable(InviteCodeDocument invite) {
        Instant now = Instant.now();

        if (invite.revokedAt != null) return false;

        if (invite.expiresAt != null && now.isAfter(invite.expiresAt)) return false;

        if (invite.maxUses != null && invite.useCount >= invite.maxUses) return false;

        return true;
    }

    private void expirePending(ObjectId inviteeId) {
        Instant now = Instant.now();
        serverInviteRepository.findByInviteeIdAndStatusOrderByCreatedAtDesc(inviteeId, InviteStatus.PENDING).stream()
                .filter(invite -> invite.expiresAt.isBefore(now))
                .forEach(invite -> {
                    invite.status = InviteStatus.EXPIRED;
                    invite.updatedAt = now;
                    serverInviteRepository.save(invite);
                    notificationService.markServerInviteRead(inviteeId, invite.id);
                });
    }

    private String uniqueCode() {
        for (int i = 0; i < 5; i++) {
            byte[] bytes = new byte[12];
            random.nextBytes(bytes);
            String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (inviteCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể sinh invite code.");
    }
}