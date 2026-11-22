package com.oldmermaidman.gomoku.backend.common;

import java.util.Map;

/** A failure that already knows its code and HTTP status; see {@link ApiError}. */
public class ApiException extends RuntimeException {

    private final ApiError error;
    private final Map<String, Object> details;

    public ApiException(ApiError error, String message) {
        this(error, message, Map.of());
    }

    public ApiException(ApiError error, String message, Map<String, Object> details) {
        super(message);
        this.error = error;
        this.details = Map.copyOf(details);
    }

    public ApiError error() {
        return error;
    }

    public Map<String, Object> details() {
        return details;
    }
}