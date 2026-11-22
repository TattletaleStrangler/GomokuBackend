package com.oldmermaidman.gomoku.backend.common;

import java.util.Map;

/**
 * The only error shape the API returns:
 * {@code {"code": "...", "message": "...", "details": {...}}}.
 */
public record ErrorResponse(String code, String message, Map<String, Object> details) {

    public static ErrorResponse of(ApiError error, String message, Map<String, Object> details) {
        return new ErrorResponse(error.name(), message, details);
    }
}