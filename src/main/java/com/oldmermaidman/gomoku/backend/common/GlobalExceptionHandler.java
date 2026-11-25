package com.oldmermaidman.gomoku.backend.common;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.telemetry.RequestIdFilter;
import com.oldmermaidman.gomoku.backend.telemetry.TelemetryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns every failure into the single error shape of {@link ErrorResponse}.
 *
 * <p>Server errors (5xx) are also written to {@code error_events} with the request id,
 * so the admin panel can show what actually broke and a player's screenshot can be
 * traced back to a stack trace. 4xx are expected answers, not errors: they are not
 * recorded.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final TelemetryService telemetry;

    public GlobalExceptionHandler(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException e, HttpServletRequest request) {
        String requestId = requestId(request);
        if (e.error().status().is5xxServerError()) {
            record(e.error(), e.getMessage(), e, request, requestId);
        }
        return ResponseEntity.status(e.error().status())
                .body(ErrorResponse.of(e.error(), e.getMessage(), e.details(), requestId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, Object> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, second) -> first));
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ApiError.VALIDATION_FAILED, "Request validation failed", details, requestId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ApiError.VALIDATION_FAILED,
                "Request body is not valid JSON",
                Map.of(),
                requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        // Never leak internals to the client, but make sure the cause is in the log and
        // in the admin panel.
        String requestId = requestId(request);
        log.error("Unhandled error [{}]", requestId, e);
        record(ApiError.INTERNAL_ERROR, e.getMessage(), e, request, requestId);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(
                ApiError.INTERNAL_ERROR, "Unexpected error", Map.of(), requestId));
    }

    private void record(
            ApiError error,
            String message,
            Exception cause,
            HttpServletRequest request,
            String requestId) {
        try {
            telemetry.recordServerError(
                    error.status().value(),
                    request.getMethod(),
                    request.getRequestURI(),
                    currentUserId(),
                    error.name(),
                    message,
                    stackTraceOf(cause),
                    requestId);
        } catch (RuntimeException recording) {
            // Recording must never turn one failure into two.
            log.warn("Could not record the server error", recording);
        }
    }

    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return id instanceof String text ? text : null;
    }

    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.id()
                : null;
    }

    private static String stackTraceOf(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}