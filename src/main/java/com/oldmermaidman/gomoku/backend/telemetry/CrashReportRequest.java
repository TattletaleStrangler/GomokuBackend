package com.oldmermaidman.gomoku.backend.telemetry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A crash the client sends on the next launch (FEATURES.md, B4).
 *
 * <p>Everything is optional except the stack trace: an older client, or one where the
 * device data could not be read, must still be able to report the crash. Nothing secret
 * belongs here — no tokens, no passwords.
 */
public record CrashReportRequest(
        @Size(max = 64) String installId,
        @Size(max = 16) String appVersion,
        Integer versionCode,
        @Size(max = 80) String device,
        @Size(max = 40) String android,
        @Size(max = 16) String locale,
        @Size(max = 80) String thread,
        @Size(max = 160) String exception,
        @Size(max = 500) String message,
        @NotBlank @Size(max = 20_000) String stacktrace) {
}