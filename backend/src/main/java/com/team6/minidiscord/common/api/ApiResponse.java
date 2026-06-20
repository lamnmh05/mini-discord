package com.team6.minidiscord.common.api;

import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        T data,
        Map<String, Object> meta,
        ApiError error
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, Map.of(), null);
    }

    public static <T> ApiResponse<T> page(T data, String nextCursor) {
        return new ApiResponse<>(true, data, nextCursor == null ? Map.of() : Map.of("nextCursor", nextCursor), null);
    }

    public static ApiResponse<Object> fail(ApiError error) {
        return new ApiResponse<>(false, null, Map.of(), error);
    }
}
