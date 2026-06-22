package com.team6.minidiscord.realtime;

import com.team6.minidiscord.MiniDiscordApplication;
import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelRepository;
import com.team6.minidiscord.channel.ChannelType;
import com.team6.minidiscord.membership.MemberRole;
import com.team6.minidiscord.membership.ServerMemberDocument;
import com.team6.minidiscord.membership.ServerMemberRepository;
import com.team6.minidiscord.security.JwtService;
import com.team6.minidiscord.server.ServerDocument;
import com.team6.minidiscord.server.ServerRepository;
import com.team6.minidiscord.support.IntegrationTestSupport;
import com.team6.minidiscord.user.AccountStatus;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MiniDiscordApplication.class, IntegrationTestSupport.IntegrationTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class MiniDiscordWebSocketIntegrationTest extends IntegrationTestSupport {
    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private ServerMemberRepository memberRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        cleanState();
    }

    @Test
    void authenticatedMemberReceivesChannelTypingEvents() throws Exception {
        UserDocument user = userRepository.save(user("alice"));
        ServerDocument server = serverRepository.save(server(user.id));
        ChannelDocument channel = channelRepository.save(channel(server.id, user.id));
        server.defaultChannelId = channel.id;
        serverRepository.save(server);
        memberRepository.save(member(server.id, user.id));

        WebSocketStompClient client = stompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtService.createAccessToken(user.id, user.username));
        StompSession session = client.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        ).get(10, TimeUnit.SECONDS);

        LinkedBlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
        session.subscribe("/topic/channels/" + channel.id.toHexString() + "/typing", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((Map<String, Object>) payload);
            }
        });

        session.send("/app/channels/" + channel.id.toHexString() + "/typing", Map.of("typing", true));

        Map<String, Object> event = events.poll(10, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event).containsEntry("eventType", "TYPING_STARTED");
        assertThat(event).containsEntry("serverId", server.id.toHexString());
        assertThat(event).containsEntry("channelId", channel.id.toHexString());
        assertThat(event.get("data")).isInstanceOfSatisfying(Map.class, data ->
                assertThat(data).containsEntry("username", "alice"));

        session.disconnect();
        client.stop();
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        client.setMessageConverter(converter);
        return client;
    }

    private UserDocument user(String username) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument();
        user.id = new ObjectId();
        user.username = username;
        user.usernameKey = username;
        user.email = username + "@example.com";
        user.emailKey = user.email;
        user.passwordHash = "encoded";
        user.displayName = username;
        user.accountStatus = AccountStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    private ServerDocument server(ObjectId ownerId) {
        Instant now = Instant.now();
        ServerDocument server = new ServerDocument();
        server.id = new ObjectId();
        server.name = "Team";
        server.createdById = ownerId;
        server.createdAt = now;
        server.updatedAt = now;
        return server;
    }

    private ChannelDocument channel(ObjectId serverId, ObjectId ownerId) {
        Instant now = Instant.now();
        ChannelDocument channel = new ChannelDocument();
        channel.id = new ObjectId();
        channel.serverId = serverId;
        channel.createdById = ownerId;
        channel.name = "general";
        channel.nameKey = "general";
        channel.type = ChannelType.TEXT;
        channel.position = 0;
        channel.createdAt = now;
        channel.updatedAt = now;
        return channel;
    }

    private ServerMemberDocument member(ObjectId serverId, ObjectId userId) {
        Instant now = Instant.now();
        ServerMemberDocument member = new ServerMemberDocument();
        member.id = new ObjectId();
        member.serverId = serverId;
        member.userId = userId;
        member.role = MemberRole.OWNER;
        member.joinedAt = now;
        member.updatedAt = now;
        return member;
    }
}
