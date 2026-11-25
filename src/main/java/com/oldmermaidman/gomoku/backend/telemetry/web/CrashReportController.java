package com.oldmermaidman.gomoku.backend.telemetry.web;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.telemetry.CrashReportRequest;
import com.oldmermaidman.gomoku.backend.telemetry.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the Android client sends a crash on its next launch (FEATURES.md, B4).
 *
 * <p>Open on purpose: a crash often happens before signing in, and the report is worth
 * more than the account it belongs to. Nothing secret is sent, and the player can switch
 * the sending off in the settings.
 */
@RestController
@RequestMapping("/api/v1")
public class CrashReportController {

    private final TelemetryService telemetry;

    public CrashReportController(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @PostMapping("/crash-reports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void report(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CrashReportRequest request) {
        telemetry.recordCrash(request, user == null ? null : user.id());
    }
}