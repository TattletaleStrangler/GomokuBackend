package com.oldmermaidman.gomoku.backend.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Writes an {@link ErrorResponse} straight into a servlet response.
 *
 * <p>Used where the MVC exception handling is not reachable: the security entry point
 * and the filters in front of it.
 */
@Component
public class ErrorWriter {

    private final ObjectMapper mapper;

    public ErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, ApiError error, String message) throws IOException {
        response.setStatus(error.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(error, message, Map.of()));
    }
}