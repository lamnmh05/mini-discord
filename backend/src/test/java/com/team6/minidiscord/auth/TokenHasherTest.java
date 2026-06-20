package com.team6.minidiscord.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {
    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void hashUsesSha256Hex() {
        assertThat(tokenHasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void newRefreshTokenIsUrlSafeAndRandomLooking() {
        String first = tokenHasher.newRefreshToken();
        String second = tokenHasher.newRefreshToken();

        assertThat(first).hasSize(64).matches("^[A-Za-z0-9_-]+$");
        assertThat(second).hasSize(64).matches("^[A-Za-z0-9_-]+$");
        assertThat(first).isNotEqualTo(second);
    }
}
