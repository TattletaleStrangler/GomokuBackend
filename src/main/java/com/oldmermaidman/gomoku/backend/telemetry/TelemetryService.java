package com.oldmermaidman.gomoku.backend.telemetry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Errors and crashes (FEATURES.md, B4).
 *
 * <p>Server errors are recorded by the exception handler, client crashes arrive from the
 * app on its next launch. Both are grouped before they reach the panel: a hundred rows
 * of one stack trace are one problem, not a hundred.
 */
@Service
public class TelemetryService {

    /** Stack traces are useful up to a point; the rest is noise in the database. */
    private static final int STACK_LIMIT = 8_000;

    private final ErrorEventRepository errors;
    private final CrashReportRepository crashes;
    private final Clock clock;

    public TelemetryService(ErrorEventRepository errors, CrashReportRepository crashes, Clock clock) {
        this.errors = errors;
        this.crashes = crashes;
        this.clock = clock;
    }

    /** Called from the exception handler for 5xx answers. */
    @Transactional
    public void recordServerError(
            int status,
            String method,
            String path,
            UUID userId,
            String errorCode,
            String message,
            String stacktrace,
            String requestId) {
        errors.save(new ErrorEventEntity(
                UUID.randomUUID(),
                requestId,
                clock.instant(),
                status,
                truncate(method, 8),
                truncate(path, 200),
                userId,
                truncate(errorCode, 40),
                truncate(message, 500),
                truncate(stacktrace, STACK_LIMIT)));
    }

    /** Stores a crash the client sent on its next launch. */
    @Transactional
    public void recordCrash(CrashReportRequest request, UUID userId) {
        crashes.save(new CrashReportEntity(
                UUID.randomUUID(),
                fingerprintOf(request.exception(), request.stacktrace()),
                truncate(request.installId(), 64),
                userId,
                truncate(request.appVersion(), 16),
                request.versionCode(),
                truncate(request.device(), 80),
                truncate(request.android(), 40),
                truncate(request.locale(), 16),
                truncate(request.thread(), 80),
                truncate(request.exception(), 160),
                truncate(request.message(), 500),
                truncate(request.stacktrace(), STACK_LIMIT),
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<ErrorEventEntity> recentErrors() {
        return errors.findFirst100ByOrderByOccurredAtDesc();
    }

    /** Crashes grouped by fingerprint, most recent group first. */
    @Transactional(readOnly = true)
    public List<CrashGroup> crashGroups() {
        Map<String, List<CrashReportEntity>> byFingerprint = new LinkedHashMap<>();
        for (CrashReportEntity crash : crashes.findFirst200ByOrderByCreatedAtDesc()) {
            byFingerprint.computeIfAbsent(crash.getFingerprint(), key -> new ArrayList<>()).add(crash);
        }
        return byFingerprint.values().stream().map(CrashGroup::of).toList();
    }

    @Transactional(readOnly = true)
    public long serverErrorsSince(Instant since) {
        return errors.countByOccurredAtAfter(since);
    }

    @Transactional(readOnly = true)
    public long crashesSince(Instant since) {
        return crashes.countByCreatedAtAfter(since);
    }

    /**
     * The same exception from the same place is the same bug, whatever else differs:
     * the hash is over the exception class, the message and the first stack frame.
     */
    private static String fingerprintOf(String exception, String stacktrace) {
        String firstFrame = stacktrace.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("at "))
                .findFirst()
                .orElse("");
        String key = (exception == null ? "" : exception) + "|" + firstFrame;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always there; a plain key still groups well enough.
            return Integer.toHexString(key.hashCode());
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}