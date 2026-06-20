package com.team6.minidiscord.security;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void createAccessTokenCanBeVerified() {
        JwtService jwtService = new JwtService("mini-discord-test", SECRET, 15);
        ObjectId userId = new ObjectId();

        String token = jwtService.createAccessToken(userId, "alice");
        AuthenticatedUser user = jwtService.verify(token);

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.username()).isEqualTo("alice");
    }

    @Test
    void verifyRejectsTokenSignedWithDifferentSecret() {
        JwtService signer = new JwtService("mini-discord-test", SECRET, 15);
        JwtService verifier = new JwtService("mini-discord-test", "abcdefghijklmnopqrstuvxyz0123456", 15);
        String token = signer.createAccessToken(new ObjectId(), "alice");

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void verifyRejectsExpiredToken() {
        JwtService jwtService = new JwtService("mini-discord-test", SECRET, -1);
        String token = jwtService.createAccessToken(new ObjectId(), "alice");

        assertThatThrownBy(() -> jwtService.verify(token))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    @Test
    void constructorRequiresLongEnoughSecret() {
        assertThatThrownBy(() -> new JwtService("mini-discord-test", "short-secret", 15))
                .isInstanceOf(IllegalStateException.class);
    }
}
