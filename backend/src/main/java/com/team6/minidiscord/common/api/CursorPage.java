package com.team6.minidiscord.common.api;

import java.util.List;

public record CursorPage<T>(
        List<T> items,
        String nextCursor
) {
}
