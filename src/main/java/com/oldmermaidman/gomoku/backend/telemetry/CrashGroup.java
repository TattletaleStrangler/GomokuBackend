package com.oldmermaidman.gomoku.backend.telemetry;

import java.time.Instant;
import java.util.List;

/**
 * One bug on the crash page: the fingerprint, the first and last time it happened, how
 * many times, and the newest report so the admin can read the stack trace.
 */
public record CrashGroup(
        String fingerprint,
        String exception,
        String message,
        int count,
        Instant firstSeen,
        Instant lastSeen,
        String lastDevice,
        String lastVersion,
        CrashReportEntity last) {

    /** [rows] come newest first, as the repository returns them. */
    public static CrashGroup of(List<CrashReportEntity> rows) {
        CrashReportEntity newest = rows.get(0);
        CrashReportEntity oldest = rows.get(rows.size() - 1);
        return new CrashGroup(
                newest.getFingerprint(),
                newest.getException(),
                newest.getMessage(),
                rows.size(),
                oldest.getCreatedAt(),
                newest.getCreatedAt(),
                newest.getDevice(),
                newest.getAppVersion(),
                newest);
    }
}