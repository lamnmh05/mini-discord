package com.team6.minidiscord.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.minidiscord.MiniDiscordApplication;
import com.team6.minidiscord.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MiniDiscordApplication.class, IntegrationTestSupport.IntegrationTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class MiniDiscordApiIntegrationTest extends IntegrationTestSupport {
    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private long stamp;

    @BeforeEach
    void setUp() {
        cleanState();
        stamp = System.nanoTime();
    }

    @Test
    void authEndpointsIssueRefreshCookieAndReturnValidationEnvelope() throws Exception {
        HttpResponse<String> invalid = request(
                HttpMethod.POST,
                "/api/v1/auth/register",
                null,
                Map.of("username", "no", "email", "invalid", "password", "short"),
                null,
                "trace-test"
        );

        JsonNode invalidBody = body(invalid);
        assertThat(invalid.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(invalid.headers().firstValue("X-Trace-Id")).contains("trace-test");
        assertThat(invalidBody.get("success").asBoolean()).isFalse();
        assertThat(invalidBody.at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(invalidBody.at("/error/traceId").asText()).isEqualTo("trace-test");

        Session alice = registerAndLogin("alice");

        HttpResponse<String> me = request(HttpMethod.GET, "/api/v1/users/me", alice, null);
        assertThat(me.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(data(me).get("id").asText()).isEqualTo(alice.userId());

        HttpResponse<String> refresh = request(
                HttpMethod.POST,
                "/api/v1/auth/refresh",
                null,
                Map.of(),
                alice.refreshCookie(),
                null
        );
        assertThat(refresh.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(data(refresh).get("accessToken").asText()).isNotBlank();
        assertThat(refreshCookie(refresh).getValue()).isNotBlank();
    }

    @Test
    void serverInviteMessageFriendAndDirectConversationFlow() throws Exception {
        Session owner = registerAndLogin("owner");
        Session member = registerAndLogin("member");

        JsonNode server = data(request(
                HttpMethod.POST,
                "/api/v1/servers",
                owner,
                Map.of("name", "Team " + stamp)
        ));
        String serverId = server.get("id").asText();
        String defaultChannelId = server.get("defaultChannelId").asText();

        HttpResponse<String> detail = request(HttpMethod.GET, "/api/v1/servers/" + serverId, owner, null);
        assertThat(detail.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(data(detail).get("currentRole").asText()).isEqualTo("OWNER");

        JsonNode inviteCode = data(request(
                HttpMethod.POST,
                "/api/v1/servers/" + serverId + "/invite-codes",
                owner,
                Map.of("maxUses", 3)
        ));
        String code = inviteCode.get("code").asText();

        HttpResponse<String> join = request(HttpMethod.POST, "/api/v1/invite-codes/" + code + "/join", member, Map.of());
        assertThat(join.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(data(join).get("id").asText()).isEqualTo(serverId);

        HttpResponse<String> members = request(HttpMethod.GET, "/api/v1/servers/" + serverId + "/members", owner, null);
        assertThat(members.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(data(members)).hasSize(2);

        JsonNode message = data(request(
                HttpMethod.POST,
                "/api/v1/channels/" + defaultChannelId + "/messages",
                member,
                Map.of(
                        "content", "hello searchable " + stamp,
                        "attachments", List.of(),
                        "clientRequestId", "msg-" + stamp
                )
        ));
        String messageId = message.get("id").asText();

        HttpResponse<String> history = request(HttpMethod.GET, "/api/v1/channels/" + defaultChannelId + "/messages?limit=1", owner, null);
        assertThat(data(history).get(0).get("id").asText()).isEqualTo(messageId);

        HttpResponse<String> search = request(
                HttpMethod.GET,
                "/api/v1/servers/" + serverId + "/messages/search?q=searchable&channelId=" + defaultChannelId,
                owner,
                null
        );
        assertThat(data(search).get(0).get("id").asText()).isEqualTo(messageId);

        HttpResponse<String> reacted = request(HttpMethod.PUT, "/api/v1/messages/" + messageId + "/reactions/ok", owner, Map.of());
        assertThat(data(reacted).at("/reactions/0/emoji").asText()).isEqualTo("ok");

        JsonNode friendRequest = data(request(
                HttpMethod.POST,
                "/api/v1/friends/requests",
                owner,
                Map.of("username", member.username())
        ));
        String requestId = friendRequest.get("id").asText();

        HttpResponse<String> accepted = request(HttpMethod.POST, "/api/v1/friends/requests/" + requestId + "/accept", member, Map.of());
        assertThat(data(accepted).at("/user/username").asText()).isEqualTo(owner.username());

        JsonNode directConversation = data(request(
                HttpMethod.POST,
                "/api/v1/direct-conversations",
                owner,
                Map.of("userId", member.userId())
        ));
        String conversationId = directConversation.get("id").asText();

        JsonNode directMessage = data(request(
                HttpMethod.POST,
                "/api/v1/direct-conversations/" + conversationId + "/messages",
                owner,
                Map.of(
                        "content", "direct hello " + stamp,
                        "attachments", List.of(),
                        "clientRequestId", "dm-" + stamp
                )
        ));

        HttpResponse<String> directHistory = request(
                HttpMethod.GET,
                "/api/v1/direct-conversations/" + conversationId + "/messages",
                member,
                null
        );
        assertThat(data(directHistory).get(0).get("id").asText()).isEqualTo(directMessage.get("id").asText());
    }

    private Session registerAndLogin(String label) throws Exception {
        String suffix = Long.toString(stamp, 36) + label;
        String username = label + suffix.substring(0, Math.min(10, suffix.length()));
        String email = username + "@example.com";
        String password = "Password123!";

        HttpResponse<String> registered = request(
                HttpMethod.POST,
                "/api/v1/auth/register",
                null,
                Map.of("username", username, "email", email, "password", password)
        );
        assertThat(registered.statusCode()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> login = request(
                HttpMethod.POST,
                "/api/v1/auth/login",
                null,
                Map.of("email", email, "password", password)
        );
        assertThat(login.statusCode()).isEqualTo(HttpStatus.OK.value());

        JsonNode data = data(login);
        return new Session(
                data.get("accessToken").asText(),
                refreshCookie(login),
                data.get("user").get("id").asText(),
                username
        );
    }

    private HttpResponse<String> request(HttpMethod method, String path, Session session, Object body) throws Exception {
        return request(method, path, session, body, null, null);
    }

    private HttpResponse<String> request(HttpMethod method, String path, Session session, Object body, Cookie cookie, String traceId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        builder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (session != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken());
        }
        if (cookie != null) {
            builder.header(HttpHeaders.COOKIE, cookie.getName() + "=" + cookie.getValue());
        }
        if (traceId != null) {
            builder.header("X-Trace-Id", traceId);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        return httpClient.send(builder.method(method.name(), publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        return body(response).get("data");
    }

    private Cookie refreshCookie(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue(HttpHeaders.SET_COOKIE).orElse(null);
        assertThat(setCookie).contains("refresh_token=");
        String token = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
        return new Cookie("refresh_token", token);
    }

    private record Session(String accessToken, Cookie refreshCookie, String userId, String username) {
    }
}
