package com.oldmermaidman.gomoku.backend.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One server error (table {@code error_events}, FEATURES.md, B4): only 5xx, and always
 * with the request id that was returned to the client, so a screenshot can be traced
 * back to a stack trace.
 */
@Entity
@Table(name = "error_events")
public class ErrorEventEntity {

    @Id
    private UUID id;

    @Column(name = "request_id", length = 36, updatable = false)
    private String requestId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false)
    private int status;

    @Column(length = 8, updatable = false)
    private String method;

    @Column(length = 200, updatable = false)
    private String path;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "error_code", length = 40, updatable = false)
    private String errorCode;

    @Column(length = 500, updatable = false)
    private String message;

    @Column(updatable = false)
    private String stacktrace;

    /** For JPA only. */
    protected ErrorEventEntity() {
    }

    public ErrorEventEntity(
            UUID id,
            String requestId,
            Instant occurredAt,
            int status,
            String method,
            String path,
            UUID userId,
            String errorCode,
            String message,
            String stacktrace) {
        this.id = id;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
        this.status = status;
        this.method = method;
        this.path = path;
        this.userId = userId;
        this.errorCode = errorCode;
        this.message = message;
        this.stacktrace = stacktrace;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public int getStatus() {
        return status;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getStacktrace() {
        return stacktrace;
    }
}