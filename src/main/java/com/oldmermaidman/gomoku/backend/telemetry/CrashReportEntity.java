package com.oldmermaidman.gomoku.backend.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One client crash (table {@code crash_reports}, FEATURES.md, B4). The client keeps the
 * report locally and sends it on the next launch; {@code fingerprint} groups equal
 * crashes so the panel shows "12 times" instead of twelve rows.
 */
@Entity
@Table(name = "crash_reports")
public class CrashReportEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64, updatable = false)
    private String fingerprint;

    @Column(name = "install_id", length = 64, updatable = false)
    private String installId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "app_version", length = 16, updatable = false)
    private String appVersion;

    @Column(name = "version_code", updatable = false)
    private Integer versionCode;

    @Column(length = 80, updatable = false)
    private String device;

    @Column(length = 40, updatable = false)
    private String android;

    @Column(length = 16, updatable = false)
    private String locale;

    @Column(length = 80, updatable = false)
    private String thread;

    @Column(length = 160, updatable = false)
    private String exception;

    @Column(length = 500, updatable = false)
    private String message;

    @Column(updatable = false)
    private String stacktrace;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected CrashReportEntity() {
    }

    public CrashReportEntity(
            UUID id,
            String fingerprint,
            String installId,
            UUID userId,
            String appVersion,
            Integer versionCode,
            String device,
            String android,
            String locale,
            String thread,
            String exception,
            String message,
            String stacktrace,
            Instant createdAt) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.installId = installId;
        this.userId = userId;
        this.appVersion = appVersion;
        this.versionCode = versionCode;
        this.device = device;
        this.android = android;
        this.locale = locale;
        this.thread = thread;
        this.exception = exception;
        this.message = message;
        this.stacktrace = stacktrace;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getInstallId() {
        return installId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public Integer getVersionCode() {
        return versionCode;
    }

    public String getDevice() {
        return device;
    }

    public String getAndroid() {
        return android;
    }

    public String getLocale() {
        return locale;
    }

    public String getThread() {
        return thread;
    }

    public String getException() {
        return exception;
    }

    public String getMessage() {
        return message;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}