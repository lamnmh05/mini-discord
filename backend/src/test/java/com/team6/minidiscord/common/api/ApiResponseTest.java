package com.team6.minidiscord.common.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void pageOmitsNextCursorWhenCursorIsNull() {
        ApiResponse<List<String>> response = ApiResponse.page(List.of("item"), null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly("item");
        assertThat(response.meta()).isEmpty();
    }

    @Test
    void pageIncludesNextCursorWhenCursorExists() {
        ApiResponse<List<String>> response = ApiResponse.page(List.of("item"), "next");

        assertThat(response.meta()).containsEntry("nextCursor", "next");
    }
}
