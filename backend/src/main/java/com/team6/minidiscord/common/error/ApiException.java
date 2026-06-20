package com.team6.minidiscord.common.error;

import java.util.List;

public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final List<String> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<String> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public List<String> details() {
        return details;
    }
}
