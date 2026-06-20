package com.team6.minidiscord.common.api;

import java.util.List;

public record ApiError(
        String code,
        String message,
        List<String> details,
        String traceId
) {
}
