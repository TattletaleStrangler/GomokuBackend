package com.oldmermaidman.gomoku.backend.common;

import java.util.Map;

/**
 * The only error shape the API returns:
 * {@code {"code": "...", "message": "...", "details": {...}, "requestId": "..."}}.
 *
 * <p>{@code requestId} is the id of the request that failed: it is also in the response
 * header and in the server log, and the admin panel can find the stack trace by it. It
 * is null only when the failure happened before a request existed.
 */
public record ErrorResponse(String code, String message, Map<String, Object> details, String requestId) {

    public static ErrorResponse of(ApiError error, String message, Map<String, Object> details) {
        return of(error, message, details, null);
    }

    public static ErrorResponse of(
            ApiError error, String message, Map<String, Object> details, String requestId) {
        return new ErrorResponse(error.name(), message, details, requestId);
    }
}