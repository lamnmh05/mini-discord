package com.team6.minidiscord.realtime;

import com.team6.minidiscord.membership.ServerMemberRepository;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;

@Service
public class PresenceService {
    private final StringRedisTemplate redisTemplate;
    private final ServerMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher publisher;
    private final PresenceLookupService lookupService;

    public PresenceService(
            StringRedisTemplate redisTemplate,
            ServerMemberRepository memberRepository,
            UserRepository userRepository,
            WebSocketEventPublisher publisher,
            PresenceLookupService lookupService
    ) {
        this.redisTemplate = redisTemplate;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.publisher = publisher;
        this.lookupService = lookupService;
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (!(accessor.getUser() instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token)
                || !(token.getPrincipal() instanceof com.team6.minidiscord.security.AuthenticatedUser user)
                || accessor.getSessionId() == null) {
            return;
        }
        String userId = user.id().toHexString();
        String connectionKey = lookupService.connectionsKey(userId);
        Long before = redisTemplate.opsForSet().size(connectionKey);
        redisTemplate.opsForSet().add(connectionKey, accessor.getSessionId());
        redisTemplate.expire(connectionKey, Duration.ofHours(12));
        redisTemplate.opsForValue().set(sessionKey(accessor.getSessionId()), userId, Duration.ofHours(12));
        if (before == null || before == 0) {
            broadcast(user.id(), "ONLINE");
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String userId = redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (userId == null || !ObjectId.isValid(userId)) {
            return;
        }
        redisTemplate.delete(sessionKey(sessionId));
        String connectionKey = lookupService.connectionsKey(userId);
        redisTemplate.opsForSet().remove(connectionKey, sessionId);
        Long remaining = redisTemplate.opsForSet().size(connectionKey);
        if (remaining == null || remaining == 0) {
            redisTemplate.delete(connectionKey);
            ObjectId id = new ObjectId(userId);
            userRepository.findById(id).ifPresent(user -> {
                user.lastSeenAt = Instant.now();
                user.updatedAt = user.lastSeenAt;
                userRepository.save(user);
            });
            broadcast(id, "OFFLINE");
        }
    }

    private void broadcast(ObjectId userId, String status) {
        memberRepository.findByUserIdOrderByJoinedAtDesc(userId).forEach(member ->
                publisher.serverPresenceEvent(member.serverId.toHexString(), WebSocketEvent.of(
                        "PRESENCE_CHANGED",
                        member.serverId.toHexString(),
                        null,
                        Map.of("userId", userId.toHexString(), "status", status)
                ))
        );
    }

    private String sessionKey(String sessionId) {
        return "presence:session:" + sessionId;
    }
}
